Plugin for ZABBIX and Rudder
==============================

This project is part of Rudder - IT automation and compliance made easy.
See: http://rudder-project.org for more information.

Mailing list, irc : https://www.rudder-project.org/site/community/mailing-lists/

Synposis
--------

This plugin aims at providing Rudder integration with Zabbix. It can automatically
add hosts to Zabbix when the corresponding node is set up for monitoring in Rudder.

The nodes can also have Zabbix macros applied using the appropriate generic method directly in
Rudder's technique editor.

Installation
------------

- Prerequisites :

  - Install a Zabbix server https://www.zabbix.com/download
    The server is not required to run on the same host as the Rudder server.
    However, this plugin has to be installed and running on the Rudder server.

  - The Rudder server must have Python installed.

- Generate the Rudder package if you do not already have the .rpkg file :
  ```
  cd zabbix
  make
  ```

  The .rpkg file will be generated in the current directory.

- Install the package on the Rudder server :
  ```
  /opt/rudder/bin/rudder-pkg install-file <plugin>.rpkg
  ```

- Edit the configuration file /opt/rudder/etc/zabbix.conf. This config file has to contain your Zabbix
  server's webservice API URL and the credentials to access it.
  You also need to provide the Rudder API URL, and a token to access it, which you will need to issue : 
  see https://www.rudder-project.org/rudder-api-doc/#api-_-Authentication

Usage
-----

Now that the plugin is installed, you can check if it is working by adding new Rudder nodes. They will be 
automatically added to your list of Zabbix hosts (and removed on deletion of the Rudder node).

This is achieved by Rudder's post-node-acceptance/deletion hooks, which are calling the plugin with the appropriate
options on such events. The plugin will then make calls to the APIs of the Rudder and Zabbix servers to handle the
modifications.

In order to configure the monitoring of the Rudder nodes, you can provide Zabbix with parameters (macros) associated to its
hosts. The "Monitoring parameter" generic method achieves this.

```
Make sure for addind monitoring parameter, the key field must be written in uppercase.
```

The data will be sent hourly to the Zabbix server. This can be changed by editing the /etc/cron.d/zabbix-rudder file.
This operation can be manually executed by running

Adding|Removing hosts
```
/opt/rudder/bin/zabbix-plugin.py update
```

Adding hosts
```
/opt/rudder/bin/zabbix-plugin.py hook addHost <id>
```

Removing hosts
```
/opt/rudder/bin/zabbix-plugin.py hook rmHost <hostname>
```
on the Rudder server.

In order to configure the monitoring to the Rudder nodes, you can provide Zabbix with templates and parameters (macros) associated to Zabbix hosts.

In Rudder, you can edit technique editors (monitoring templates - monitoring parameters) through generic methods provided by Rudder interface, assign then to a node via rule and eventually monitor theses directives by Zabbix.

Once you done this, automatically a csv files will be creted to store theses directive names, to have a syncronization between Rudder and Zabbix ressources (Hosts - Templates - Macros).

This operation can be mannually executed by running:
```
/opt/rudder/bin/zabbix-plugin.py apply-configuration
```

Authors
-------

Normation http://normation.com
Victor Querette victor.querette@normation.com
Nabil El khalii nabil.el-khalii@normation.com

Contributing
------------

Thank you for your interest in our project !
The contribution process is detailed here:
https://www.rudder-project.org/site/documentation/how-to-contribute/
