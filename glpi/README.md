Plugin for GLPI and Rudder
==============================

This project is part of Rudder - IT automation and compliance made easy.
See: http://rudder-project.org for more information.

Mailing list, irc : https://www.rudder-project.org/site/community/mailing-lists/

Synposis
--------

This plugin provides integration between Rudder and GLPI.
It will send to your GLPI server the up-to-date inventories processed by the Rudder server.

Installation
------------

- Prerequisites :

  - Install a GLPI server https://glpi-project.org/
    The server is not required to be running on the same host as the Rudder server.
    However, this plugin has to be installed and running on the Rudder server.

  - Install the fusion inventory plugin for GLPI https://github.com/fusioninventory/fusioninventory-for-glpi/releases

- Generate the Rudder package if you do not already have the .rpkg file :
  ```
  cd glpi
  make
  ```

  The .rpkg package file will be generated in the current directory.

- Install the package on the Rudder server :
  ```
  /opt/rudder/bin/rudderpkg install-file <plugin>.rpkg
  ```

- Edit the configuration file in /opt/rudder/etc/glpi.conf. This config file must contain the URL to your
  GLPI server's fusion inventory plugin.

Usage
-----

Run /opt/rudder/bin/glpi-plugin send-all to update all the inventories. This command is run automatically
everyday around 7AM.
To manually update an inventory for a specific node, run /opt/rudder/bin/glpi-plugin send-one <node UUID>.
The plugin will automatically send a new inventory upon node addition to Rudder.

Authors
-------

Normation http://normation.com
Victor Querette victor.querette@normation.com

Contributing
------------

Thank you for your interest in our project !
The contribution process is detailed here:
https://www.rudder-project.org/site/documentation/how-to-contribute/
