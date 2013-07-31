# ovirt.client

A Clojure library to provision virtual machines in ovirt

## Usage
```
 Switches           Default  Desc                                                                             
 --------           -------  ----                                                                             
 -r, --ovirt-url             The URL for ovirt server api - be sure to always include port, even if 80 or 443 
 -u, --username              ovirt username                                                                   
 -p, --password              ovirt password                                                                   
 -c, --cluster               Name of the cluster to start the vm in                                           
 -n, --name                  Name of the vm to create (will destroy existing if already exists)               
 -t, --template              Name of the template to use to create the vm                                     
 -m, --memory       512      Amount of RAM (in MB) to give the vm                                             
 -o, --output-file           File to write the IP address of the newly created vm to                          

```

## License

Copyright © 2013 

Distributed under the Eclipse Public License, the same as Clojure.
