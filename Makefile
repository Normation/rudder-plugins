#####################################################################################
# Copyright 2017 Normation SAS
#####################################################################################

.DEFAULT_GOAL := unlicensed

include makefiles/global-vars.mk

PUB_LIBS = plugins-common 
PRIV_LIBS = plugins-common-private
LIBS= $(PUB_LIBS) $(PRIV_LIBS)

PLUGINS = blank-template helloworld datasources node-external-reports
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
	
clean: 
	rm -f pom.xml
	for i in $(ALL); do cd $$i; $(MAKE) clean; cd ..; done

very-clean: clean 
	./makefiles/find_m2_repo.sh clean
	

.PHONY: $(LIBS) $(PLUGINS)
