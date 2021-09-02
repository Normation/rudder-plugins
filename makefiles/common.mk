# Common make directives for all plugins based on a jar. 
# make all will build $(NAME).rpkg. 
# make licensed will build a license limited version of the plugin
#

.DEFAULT_GOAL := all

# define those variable before including
#OTHER_ARCHIVES = 
#SCRIPTS = postinst
#FILES = 

include ../makefiles/global-vars.mk 

COMMIT_ID       = $(shell git rev-parse HEAD 2>/dev/null || true)
BUILD_TIMESTAMP = $(shell date --iso-8601=seconds)

# build the default oss version of the package
all: std-files $(FULL_NAME)-$(VERSION).rpkg

# build a "licensed" version of the plugin, limited by a license file and verified by a public key
licensed: licensed-files $(FULL_NAME)-$(VERSION).rpkg

$(FULL_NAME)-$(VERSION).rpkg: target/metadata target/files.txz target/scripts.txz $(OTHER_ARCHIVES)
	ar r $(FULL_NAME)-$(VERSION).rpkg target/metadata target/files.txz target/scripts.txz $(OTHER_ARCHIVES)

target/scripts.txz: $(addprefix packaging/,$(SCRIPTS))
	tar cJ -C packaging -f target/scripts.txz $(SCRIPTS)

target/files.txz: $(addprefix target/,$(FILES))
	test -n "$(FILES)"   # you must define the FILES variable in your Makefile
	tar cJ -C target -f target/files.txz $(FILES)

target/metadata:
	mkdir -p target
	cp packaging/metadata target/metadata
	sed -i -e "s/\$${plugin-id}/$(FULL_NAME)/g" target/metadata
	sed -i -e "s/\$${plugin-version}/$(VERSION)/g" target/metadata
	sed -i -e "s/\$${maven.build.timestamp}/$(BUILD_TIMESTAMP)/g" target/metadata
	sed -i -e "s/\$${commit-id}/$(COMMIT_ID)/g" target/metadata
	sed -i -e "s/\$${plugin-name}/$(NAME)/g" target/metadata

clean:
	rm -f  $(FULL_NAME)-*.rpkg pom.xml
	rm -rf target

.PHONY: all licensed clean std-files licensed-files nightly nightly-licensed std-files-nightly 
