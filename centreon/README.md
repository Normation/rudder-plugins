Plugin for Centreon and Rudder
==============================

This project is part of Rudder - IT automation and compliance made easy.
See: http://rudder-project.org for more information.

Mailing list, irc : https://www.rudder-project.org/site/community/mailing-lists/

Synposis
--------

This plugin aims at providing Rudder integration with Centreon. It can automatically
add hosts to Centreon when the corresponding node is set up for monitoring in Rudder.

The nodes can also have monitoring templates applied using the appropriate generic methods
in the technique editor.

Installation
------------

- Prerequisites :

  - Install a Centreon server https://download.centreon.com
    The server is not required to be running on the same host as the Rudder server.
    However, this plugin has to be installed and running on the Rudder server.

  - The Rudder server must have Python installed.

- Generate the Rudder package if you do not already have the .rpkg file :
  ```
  cd centreon
  make
  ```

  The .rpkg package file will be generated in the current directory.

- Install the package on the Rudder server :
  ```
  /opt/rudder/bin/rudderpkg install-file <plugin>.rpkg
  ```

- Edit the configuration file /opt/rudder/etc/centreon.conf. This config file has to contain your Centreon
  server's webservice API URL, the credentials to access it, and the name of the Centreon poller used to
  monitor Rudder nodes (Central by default). You also need to provide the Rudder API URL, and a token to access
  it, which you will need to issue : see https://www.rudder-project.org/rudder-api-doc/#api-_-Authentication

Usage
-----

Now that the plugin is installed, you can check if it is working by adding new Rudder nodes. They will be 
automatically added to your list of Centreon hosts (and removed on deletion of the Rudder node).

This is achieved by Rudder's post-node-acceptance/deletion hooks, which are calling the plugin with the appropriate
options on such events. The plugin will then make calls to the APIs of the Rudder and Centreon servers to handle the
modifications.

Centreon comes with the possiblity to add monitoring templates to its hosts. You can create templates in
Centreon or check for available ones. Once you know the name of a suitable template for a Rudder node/nodegroup,
you can add it using the "Monitoring template" generic method from the technique editor.

In order to configure the monitoring of the Rudder nodes, you can provide Centreon with parameters associated to its
hosts. The "Monitoring parameter" generic method achieves this.

The data will be sent hourly to the Centreon server. This can be changed by editing the /etc/cron.d/centreon-rudder file.
This operation can be manually executed by running
```
/opt/rudder/bin/centreon-plugin.py commit
```
on the Rudder server.

Authors
-------

Normation http://normation.com
Victor Querette victor.querette@normation.com

Contributing
------------

Thank you for your interest in our project !
The contribution process is detailed here:
https://www.rudder-project.org/site/documentation/how-to-contribute/
