#
# make all : will build .rpkg.
# make licensed : will build a license limited version of the plugin
#
SHELL := /bin/bash

FILES = bin/ share/ etc/
SCRIPTS = postinst

include ../makefiles/common-plugin.mk
targets = $(addprefix target/, $(FILES))

PYZABBIX_VERSION = 0.7.5

$(targets): target/%: %
	cp -a $< target/

$(PYZABBIX_VERSION).zip:
	# wget https://github.com/lukecyca/pyzabbix/archive/$(PYZABBIX_VERSION).zip
	wget http://repository.rudder.io/build-dependencies/pyzabbix/$(PYZABBIX_VERSION).zip

share/: $(PYZABBIX_VERSION).zip
	unzip -o $(PYZABBIX_VERSION).zip
	mkdir -p share/python
	mv pyzabbix-$(PYZABBIX_VERSION)/pyzabbix share/python/
	rm -r pyzabbix-$(PYZABBIX_VERSION)

clean:
	rm -f $(PYZABBIX_VERSION).zip
	rm -f rudder-plugin-zabbix-*.rpkg pom.xml
	rm -rf target share/python/pyzabbix pyzabbix-$(PYZABBIX_VERSION)
