Rudder notifications plugin
===========================

This project is part of Rudder - IT automation and compliance made easy.
See: http://rudder-project.org for more information.

Mailing list, irc : https://www.rudder-project.org/site/community/mailing-lists/

Synposis
--------

This plugin aims at providing non-compliance notification modules for your Rudder server.
It currently supports e-mail and slack notifications.

Installation
------------

- Prerequisites :
    
    - Your Rudder server must have python-requests and python-configparser installed.

- Generate the Rudder package if you do not already have the .rpkg file :
  ```
  cd rudder-notify
  make
  ```

  The .rpkg package file will be generated in the current directory.

- Install the package on the Rudder server :
  ```
  /opt/rudder/bin/rudderpkg install-file <plugin>.rpkg
  ```

- Edit the configuration file in `/opt/rudder/etc/notify.conf`. This config file sets which notifications are enabled
  and some additional parameters.

Usage
-----

The plugin has to be started as a daemon and will watch for non-compliances on your Rudder infrastructure.
Start it with `systemctl start rudder-notify`.

The plugins takes its config from `/opt/rudder/etc/notify.conf`. Edit this file to your preferences.

For the e-mail plugin, the plugin uses the `mail` utility to send its notifications. This program needs to be installed
and properly set up. The e-mail notifications can be set in the conf file to not spam you, and send by batches of a certain
period for you to set. The conf file also contains the recipients of the e-mails.

The Slack module needs Slack webhooks to be able to send Slack messages to users or channels. You will have to declare an app
for your workspace and create these webhooks via your Slack admin panel. More info here : https://api.slack.com/incoming-webhooks 


GLPI Integration
-------

The plugin can be configure to open ticket in GLPI based on non-compliance in Rudder.
To do so, in `/opt/rudder/etc/notify.conf`, set the GLPI plugin `on` and fulfill the required parameters.
* `userToken` can be found in the `Users settings` page in the GLPI interface under the `APIToken` field.
* `apiToken` and `url` can be found under the `Setup -> General -> API` pagae in the GLPI interface.

Authors
-------

Normation http://normation.com
Victor Querette victor.querette@normation.com

Contributing
------------

Thank you for your interest in our project !
The contribution process is detailed here:
https://www.rudder-project.org/site/documentation/how-to-contribute/
