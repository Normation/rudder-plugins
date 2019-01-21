#
# make all : will build .rpkg.
# make licensed : will build a license limited version of the plugin
#
SHELL := /bin/bash

FILES = bin/ share/ etc/
SCRIPTS = postinst

include ../makefiles/common-plugin.mk
targets = $(addprefix target/, $(FILES))

$(targets): target/%: %
	cp -a $< target/

master.zip:
	wget https://github.com/adubkov/py-zabbix/archive/1.1.4.zip

share/: master.zip
	unzip master.zip
	mkdir -p share/python
	mv py-zabbix-master/{zabbix,pyzabbix} share/python/
	rm -r py-zabbix-master

clean:
	rm -f master.zip
	rm -f rudder-plugin-zabbix-*.rpkg pom.xml
	rm -rf target share/python/{zabbix,pyzabbix} py-zabbix-master
