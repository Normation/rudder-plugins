#
# make all : will build .rpkg.
# make licensed : will build a license limited version of the plugin
#
SHELL := /bin/bash

FILES = bin/ share/ etc/
SCRIPTS = postinst

include ../makefiles/common-plugin.mk
targets = $(addprefix target/, $(FILES))

py-zabbix:
	wget https://github.com/adubkov/py-zabbix/archive/master.zip
	unzip master.zip && rm master.zip
	mv py-zabbix-master/{zabbix,pyzabbix} share/python/
	rm -r py-zabbix-master

$(targets): target/%: % py-zabbix
	cp -a $< target/

clean:
	rm -f rudder-plugin-zabbix-*.rpkg pom.xml
	rm -rf target share/python/{zabbix,pyzabbix} py-zabbix-master
