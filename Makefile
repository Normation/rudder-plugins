#####################################################################################
# Copyright 2017 Normation SAS
#####################################################################################

.DEFAULT_GOAL := unlicensed

PUB_LIBS = plugins-common 
PRIV_LIBS = plugins-common-private
LIBS= $(PUB_LIBS) $(PRIV_LIBS)

PLUGINS = helloworld datasources node-external-reports
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

clean:
	for i in $(ALL); do cd $$i; $(MAKE) clean; cd ..; done

.PHONY: $(LIBS) $(PLUGINS)
