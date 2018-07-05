#####################################################################################
# Copyright 2017 Normation SAS
#####################################################################################

.DEFAULT_GOAL := unlicensed

include makefiles/global-vars.mk

PUB_LIBS = 
PRIV_LIBS = plugins-common-private
LIBS= $(PUB_LIBS) $(PRIV_LIBS)

PLUGINS = api-authorizations branding datasources helloworld node-external-reports scale-out-relay
PLUGINS-LICENSED = $(addsuffix -licensed,$(PLUGINS))
ALL = $(LIBS) $(PLUGINS)

# all 
all: unlicensed

unlicensed: $(PUB_LIBS) $(PLUGINS)

licensed: $(LIBS) $(PLUGINS-LICENSED) 

$(LIBS):%:
	cd $@ && make

$(PLUGINS):%:
	cd $@ && make

$(PLUGINS-LICENSED):%-licensed:
	cd $* && make licensed

generate-all-pom: generate-pom
	for i in $(ALL); do cd $$i; $(MAKE) generate-pom; cd ..; done

rudder-plugins.adoc:
	rm -f rudder-plugins.adoc
	for i in $(PLUGINS); do cd $$i; [ -f README.adoc ] && sed '1,/\/\/ ====doc====/d' README.adoc >> ../rudder-plugins.adoc; cd ..; done

plugins-doc-assets:
	mkdir -p plugins-doc-assets
	for i in $(PLUGINS); do cd $$i; [ -d docs ] && cp -r docs/* ../plugin-doc-assets/; cd ..; done

clean: 
	rm -f pom.xml
	for i in $(ALL); do cd $$i; $(MAKE) clean; cd ..; done

very-clean: clean 
	./makefiles/find_m2_repo.sh clean
	

.PHONY: $(LIBS) $(PLUGINS) rudder-plugins.adoc plugins-doc-assets
