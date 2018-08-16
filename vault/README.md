Plugin for Vault and Rudder
==============================

This project is part of Rudder - IT automation and compliance made easy.
See: http://rudder-project.org for more information.

Mailing list, irc : https://www.rudder-project.org/site/community/mailing-lists/

Synposis
--------

This plugin provides a possibility to fetch secrets from a Vault server and use them as Rudder
variables directly on the agent. The Rudder server itself does not need to have access to the Vault.

Installation
------------

- Prerequisites :

  - A Rudder server.

- Generate the Rudder package if you do not already have the .rpkg file :
  ```
  cd vault
  make
  ```

  The .rpkg file will be generated in the current directory.

- Install the package on the Rudder server :
  ```
  /opt/rudder/bin/rudderpkg install-file <plugin>.rpkg
  ```

- Edit the configuration file in /var/rudder/plugin-resources/vault.json on each agent. This config file must contain
  the address of your Vault server, credentials to access it and the auth mode you want to use. A sample config is in
  share/plugins/vault/sample_vault.json.

Usage
-----

Use the variable_from_vault generic method in Rudder to fetch secrets. Make sure the agents the generic method is being
used on have a proper vault.json configuration. A sample config is provided at /opt/rudder/share/plugins/vault/sample_vault.json.
This file needs you Vault server address, the configuration for at least one auth mode, and the name of the auth mode to be used.
Auth modes can be "token", "userpass" or "tls".

Authors
-------

Normation http://normation.com
Victor Querette victor.querette@normation.com

Contributing
------------

Thank you for your interest in our project !
The contribution process is detailed here:
https://www.rudder-project.org/site/documentation/how-to-contribute/
