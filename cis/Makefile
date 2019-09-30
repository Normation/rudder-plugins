#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = ncf
SCRIPTS = postinst prerm cis.json rudder-synchronize
OTHER_ARCHIVES = target/configuration.txz

include ../makefiles/common-plugin.mk
targets = $(addprefix target/, $(FILES))

$(targets): target/%: % target/configuration.txz
	cp -ar $< target/

configuration: target/configuration.txz

target/configuration.txz:
	cp -ar configuration/rules target/
	cp -ar configuration/directives target/
	cp -ar configuration/techniques target/
	tar cJ -C target -f target/configuration.txz rules directives techniques

ncf:
	cp -ar configuration/ncf .

packaging/cis.json:
	tools/cis-tag

clean:
	rm -f  $(FULL_NAME)-*.rpkg pom.xml packaging/cis.json
	rm -rf target ncf
	rm -rf configuration/rules/*
