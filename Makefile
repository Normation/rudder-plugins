#####################################################################################
# Copyright 2017 Normation SAS
#####################################################################################

.DEFAULT_GOAL := all

PLUGINS = helloworld centreon datasources node-external-reports

# all
all: $(PLUGINS)

$(PLUGINS):%:
	cd $@ && make


.PHONY: $(PLUGINS)
