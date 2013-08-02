(ns ovirt.client
  (:require [clojure.tools.cli :as cli]
            [slingshot.slingshot :refer [try+ throw+]])
  (:import [org.ovirt.engine.sdk Api]
           [org.ovirt.engine.sdk.entities VM Action]))

(def ^:private init
  (do (com.redhat.qe.tools.SSLCertificateTruster/trustAllCerts)
      (com.redhat.qe.tools.SSLCertificateTruster/trustAllCertsForApacheXMLRPC)))

(def ^:dynamic *action-timeout* 600000)
(def ^:dynamic *kill-instance-when-finished* true)

(defrecord InstanceDefinition [name template-name memory sockets cores])

(defmacro loop-with-timeout
  "Similar to clojure.core/loop, but adds a timeout to break out of
   the loop if it takes too long. timeout is in ms. bindings are the
   bindings that would be provided to clojure.core/loop. body is the
   loop body to execute if the timeout has not been reached. timeout-body
   is the body to execute if the timeout has been reached. timeout-body
   defaults to throwing a RuntimeException."
  [timeout bindings body & [timeout-body]]
  `(let [starttime# (System/currentTimeMillis)]
     (loop ~bindings
       (if  (> (- (System/currentTimeMillis) starttime#) ~timeout)
         ~(or timeout-body `(throw (RuntimeException. (str "Hit timeout of " ~timeout "ms."))))
         ~body))))

(defn wait-for
  "Wait for pred to become true on instance vm (refreshing
  periodically)"
  [vm pred & [timeout]]
  (loop-with-timeout (or timeout *action-timeout*) [vm vm]
    (if (pred vm)
      vm
      (do
        (Thread/sleep 10000)
        (recur (.update vm))))))

(defn disk-ready? [vm]
  (-> vm .getDisks .list first .getStatus .getState (= "ok")))

(defn is-state? [state vm]
  (-> vm .getStatus .getState (= state)))

(def up? (partial is-state? "up"))
(def down? (partial is-state? "down"))

(defn connect [url user password]
  (Api. url user password))

(defn get-by-name [api name]
  (-> api .getVMs (.get name)))

(defn create [api cluster-name instance-def]
  (let [cluster (-> api .getClusters (.get cluster-name))
        template (-> api .getTemplates (.get (:template-name instance-def)))
        topo (doto (-> template .getCpu .getTopology)
               (.setSockets (-> instance-def :sockets int))
               (.setCores (-> instance-def :cores int)))
        vm (doto (VM.)
             (.setName (:name instance-def))
             (.setTemplate template)
             (.setCluster cluster)
             (.setMemory (:memory instance-def))
             (.setCpu (doto (.getCpu template)
                        (.setTopology topo))))]
    (-> api .getVMs (.add vm))))



(defn start [vm]
  (let [vm (wait-for vm disk-ready? 120000)
        action (doto (Action.) (.setVm (VM.)))]
    (.start vm action)
    (wait-for vm up? 240000)))

(defn stop [vm]
  (if (not (down? vm))
    (let [action (doto (Action.) (.setVm (VM.)))]
      (.stop vm action)
      (wait-for vm down? 240000))
    vm))

(defn delete [vm]
  (.delete vm))

(defn ip-address "get the ip address of the given vm"
  [vm]
  (-> vm .getGuestInfo .getIps .getIPs first .getAddress))

(def provision (comp start create))
(def unprovision (comp delete stop))

(defn provision-all
  "Provision vms with given properties (a list of InstanceDefinition),
   in parallel. Returns a list of VM objects from ovirt."
  [api cluster-name instance-defs]
  (let [provision (partial provision api cluster-name)]
    (->> (for [instance-def instance-defs]
           (future (provision instance-def)))
         doall
         (map deref))))

(defn unprovision-all
  "Destroy all the given vms, in parallel."
  [vms]
  (let [results (for [f (doall (for [i vms]
                                 (future (unprovision i))))]
                  (try+ (deref f *action-timeout* {:type ::unprovision-timed-out}) 
                        (catch [:type ::unprovision-failed] e e)))
        grouped (group-by :type results)]
    (if (or (seq (::unprovision-timed-out grouped))
            (seq (::unprovision-failed grouped)))
      (throw+ {:type ::some-unprovisisons-failed
               :results grouped})
      results)))


(def parseLong #(Long/parseLong %))

(def argspec
  [["-r" "--ovirt-url" "The URL for ovirt server api - be sure to always include port, even if 80 or 443"]
   ["-u" "--username" "ovirt username"]
   ["-p" "--password" "ovirt password"]
   ["-c" "--cluster" "Name of the cluster to start the vm in"]
   ["-n" "--name" "Name of the vm to create (will destroy existing if already exists)"]
   ["-t" "--template" "Name of the template to use to create the vm"]
   ["-m" "--memory" "Amount of RAM (in MB) to give the vm" :parse-fn parseLong :default 512]
   ["-o" "--output-file" "File to write the IP address of the newly created vm to"]
   ["--sockets" "Number of CPU sockets for the vm" :parse-fn parseLong :default 4]
   ["--cores" "Number of CPU cores per socket for the vm" :parse-fn parseLong :default 1]])

(defn -main [& args]
  (let [[{:keys [ovirt-url username password cluster name
                 template memory output-file sockets cores]}
         _ docstring] (apply cli/cli args argspec)]
    (if (not (every? identity [ovirt-url username password cluster name template memory sockets cores]))
      (println docstring)
      (let [api (connect ovirt-url username password)
            vm (get-by-name api name)
            instance-def (->InstanceDefinition name template (* memory 1024 1024) sockets cores)]
        (try (when vm
               (unprovision vm))
             (let [vm (provision api cluster instance-def)]
               (spit (or output-file (format "ovirt-instance-address-%s.txt" name))
                     (ip-address vm)))
             (finally (.shutdown api)))))))
