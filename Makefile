#####################################################################################
# Copyright 2017 Normation SAS
#####################################################################################

.DEFAULT_GOAL := unlicensed

include makefiles/global-vars.mk


PLUGINS = $(shell find . -path ./src -prune -o -name "build.conf" -printf '%P\n' | cut -d "/" -f1 | grep -v "plugins-common" | xargs echo)
SCALA_PLUGINS = $(shell find . -path ./src -prune -o -name "pom-template.xml" -printf '%P\n' | cut -d "/" -f1 | grep -v "plugins-common" | xargs echo)
PLUGINS-LICENSED = $(addsuffix -licensed,$(PLUGINS))
ALL = $(PLUGINS)

# all 
all: unlicensed

unlicensed: $(PLUGINS)

licensed: $(PLUGINS-LICENSED) 

$(PLUGINS):%:
	cd $@ && make

$(PLUGINS-LICENSED):%-licensed:
	cd $* && make licensed

generate-all-pom:
	for i in $(SCALA_PLUGINS); do cd $$i; $(MAKE) generate-pom; cd ..; done

doc-pages:
	mkdir -p doc/pages
	for i in $(PLUGINS); do cd $$i; [ -f README.adoc ] && sed '1,/\/\/ ====doc====/d' README.adoc >> ../doc/pages/$$i.adoc; cd ..; done

doc-assets:
	mkdir -p doc/assets
	for i in $(PLUGINS); do cd $$i; [ -d docs ] && cp -r docs/* ../doc/assets/; cd ..; done

doc: doc-assets doc-pages

scala-plugins-list:
	@echo $(SCALA_PLUGINS)

clean: 
	rm -f pom.xml
	rm -rf doc
	for i in $(ALL); do cd $$i; $(MAKE) clean; cd ..; done

very-clean: clean 
	./makefiles/find_m2_repo.sh clean
	
optipng:
	find . -name "*.png" -exec optipng -strip all {} \;

.PHONY: $(PLUGINS) doc
