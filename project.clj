(defproject com.redhat.qe/ovirt.client "0.1.0-SNAPSHOT"
  :description "A clojure client for ovirt and rhevm"
  :url "https://github.com/RedHatQE/ovirt.client"
  :scm {:name "git"
        :url "https://github.com/RedHatQE/ovirt.client"}
  :main ovirt.client
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [slingshot "0.10.3"]
                 [com.redhat.qe/xmlrpc-client-tools "1.0.3"]
                 [org.clojure/tools.cli "0.2.2"]
                 [org.ovirt.engine.sdk/ovirt-engine-sdk-java "1.0.0.10-1"]])
