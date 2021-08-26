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
all: VERSION = ${RUDDER_VERSION}-${PLUGIN-VERSION}
all: PLUGIN_POM_VERSION = $(RUDDER_VERSION)-${PLUGIN_VERSION}
all: RUDDER_BUILD_VERSION = $(RUDDER_VERSION)
all: RUDDER_POM_VERSION = $(RUDDER_VERSION)
all: std-files $(FULL_NAME)-$(VERSION).rpkg

nightly: VERSION = ${RUDDER_VERSION}-${PLUGIN-VERSION}-nightly
nightly: PLUGIN_POM_VERSION = $(RUDDER_VERSION)-${PLUGIN_VERSION}-SNAPSHOT
nightly: RUDDER_BUILD_VERSION = $(RUDDER_VERSION)
nightly: RUDDER_POM_VERSION = $(RUDDER_VERSION)
nightly: LIB_SUFFIX = -nightly
nightly: std-files $(FULL_NAME)-$(VERSION).rpkg

next: VERSION = ${RUDDER_VERSION_NEXT}-${PLUGIN_VERSION}-nightly
next: PLUGIN_POM_VERSION = $(RUDDER_VERSION_NEXT)-${PLUGIN_VERSION}-SNAPSHOT
next: RUDDER_BUILD_VERSION = $(RUDDER_VERSION_NEXT)-SNAPSHOT
next: RUDDER_POM_VERSION = $(RUDDER_VERSION_NEXT)
next: LIB_SUFFIX = -next
next: std-files $(FULL_NAME)-$(VERSION).rpkg

# build a "licensed" version of the plugin, limited by a license file and verified by a public key
licensed: VERSION = ${RUDDER_VERSION}-${PLUGIN-VERSION}
licensed: PLUGIN_POM_VERSION = $(RUDDER_VERSION)-${PLUGIN_VERSION}
licensed: RUDDER_BUILD_VERSION = $(RUDDER_VERSION)
licensed: RUDDER_POM_VERSION = $(RUDDER_VERSION)
licensed: licensed-files $(FULL_NAME)-$(VERSION).rpkg

nightly-licensed: VERSION = ${RUDDER_VERSION}-${PLUGIN-VERSION}-nightly
nightly-licensed: PLUGIN_POM_VERSION = $(RUDDER_VERSION)-${PLUGIN_VERSION}-SNAPSHOT
nightly-licensed: RUDDER_BUILD_VERSION = $(RUDDER_VERSION)
nightly-licensed: RUDDER_POM_VERSION = $(RUDDER_VERSION)
nightly-licensed: LIB_SUFFIX = -nightly
nightly-licensed: licensed-files $(FULL_NAME)-$(VERSION).rpkg

next-licensed: VERSION = ${RUDDER_VERSION_NEXT}-${PLUGIN_VERSION}-nightly
next-licensed: PLUGIN_POM_VERSION = $(RUDDER_VERSION_NEXT)-${PLUGIN_VERSION}-SNAPSHOT
next-licensed: RUDDER_BUILD_VERSION = $(RUDDER_VERSION_NEXT)-SNAPSHOT
next-licensed: RUDDER_POM_VERSION = $(RUDDER_VERSION_NEXT)
next-licensed: LIB_SUFFIX = -next
next-licensed: licensed-files $(FULL_NAME)-$(VERSION).rpkg

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
