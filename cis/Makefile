#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = configuration-repository
SCRIPTS = postinst prerm cis.json rudder-synchronize
OTHER_ARCHIVES = target/configuration.txz

include ../makefiles/common-plugin.mk

configuration: target/configuration.txz

target/configuration.txz: packaging/cis.json
	cp -ar configuration/rules target/
	cp -ar configuration/directives target/
	cp -ar configuration/techniques target/
	tar cJ -C target -f target/configuration.txz rules directives techniques

target/configuration-repository: configuration
	mkdir -p target/configuration-repository
	cp -ar configuration/ncf target/configuration-repository/

packaging/cis.json:
	tools/cis-tag

clean:
	rm -f  $(FULL_NAME)-*.rpkg pom.xml packaging/cis.json
	rm -rf target ncf
	rm -rf configuration/rules/*
