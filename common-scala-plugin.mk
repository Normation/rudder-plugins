# Common make directives for all plugins based on a jar. 
# make all will build $(NAME).rpkg. 
# make demo will build a license limited version of the plugin
#

RUDDER_BRANCH = $(shell sed -ne '/^rudder-branch=/s/rudder-branch=//p' build.conf)
PLUGIN_BRANCH = $(shell sed -ne '/^plugin-branch=/s/plugin-branch=//p' build.conf)
VERSION = $(RUDDER_BRANCH)-$(PLUGIN_BRANCH)
FULL_NAME = $(shell sed -ne '/^plugin-id=/s/plugin-id=//p' build.conf)
NAME = $(shell echo $(FULL_NAME) | sed -ne 's/rudder-plugin-//p')
MAVEN_OPTS = --batch-mode -U
COMMIT_ID = $(shell git rev-parse HEAD 2>/dev/null || true)
BUILD_TIMESTAMP = $(shell date --iso-8601=seconds)

## for demo
# standard destination path for the license file is in module directory, "license.sign"
TARGET_LICENSE_PATH = /opt/rudder/share/plugins/$(NAME)/license
# standard destination path for the key is at JAR root, name: $(NAME).pubkey
# we use a per-plugin name because on the classpath, the first resource for 
# a given path is loaded.
TARGET_KEY_CLASSPATH = $(FULL_NAME).pubkey
# SIGNED_LICENSE_PATH: path towards the license file to embed
# PUBLIC_KEY_PATH: path towards the public key to embed

# build the default oss version of the package
all: std-files $(FULL_NAME)-$(VERSION).rpkg

std-files: common-files std-jar prepare-files target/files.txz

# build a "demo" version of the plugin, limited by a license file and verified by a public key
demo: demo-files $(FULL_NAME)-$(VERSION).rpkg

demo-files: common-files check-demo demo-jar prepare-files add-license target/files.txz

$(FULL_NAME)-$(VERSION).rpkg: 
	ar r $(FULL_NAME)-$(VERSION).rpkg target/metadata target/files.txz target/scripts.txz

common-files: target/metadata target/scripts.txz

target/scripts.txz:
	tar cJ -C packaging -f target/scripts.txz postinst

target/metadata: prepare-files
	cp packaging/metadata target/metadata
	sed -i -e "s/\$${plugin-id}/$(FULL_NAME)/g" target/metadata
	sed -i -e "s/\$${plugin-version}/$(VERSION)/g" target/metadata
	sed -i -e "s/\$${maven.build.timestamp}/$(BUILD_TIMESTAMP)/g" target/metadata
	sed -i -e "s/\$${commit-id}/$(COMMIT_ID)/g" target/metadata
	sed -i -e "s/\$${plugin-name}/$(NAME)/g" target/metadata

# plugin-resources must be implemented for each plugin.
# the goal is to copy the plugin needed resources into target/$(NAME)
# these resources will be installed into /opt/rudder/share/plugins/$(NAME)/
target/files.txz: plugin-resources
	cp target/$(NAME).jar target/$(NAME)/
	tar cJ -C target -f target/files.txz $(NAME)

check-demo: 
	test -n "$(SIGNED_LICENSE_PATH)"  # $$SIGNED_LICENSE_PATH must be defined
	test -n "$(PUBLIC_KEY_PATH)"      # $$PUBLIC_KEY_PATH must be defined

prepare-files:
	mkdir -p target/$(NAME)

add-license:
	# embed license file since we are in demo limited build
	cp $(SIGNED_LICENSE_PATH) target/$(NAME)/license

std-jar:
	mvn $(MAVEN_OPTS) package
	mv target/$(NAME)-*-plugin-with-own-dependencies.jar target/$(NAME).jar

demo-jar:
	mvn $(MAVEN_OPTS) -Dlimited -Dplugin-resource-publickey=$(TARGET_KEY_CLASSPATH) -Dplugin-resource-license=$(TARGET_LICENSE_PATH) -Dplugin-declared-version=$(VERSION) package
	mv target/$(NAME)-*-plugin-with-own-dependencies.jar target/$(NAME).jar
	cp $(PUBLIC_KEY_PATH) target/$(TARGET_KEY_CLASSPATH)
	jar -uf target/$(NAME).jar -C target $(TARGET_KEY_CLASSPATH)

clean:
	rm -f  $(FULL_NAME)-$(VERSION).rpkg
	rm -rf target

