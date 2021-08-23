# Common make directives for all plugins based on a jar. 
# make all : will build $(NAME).rpkg. 
# make licensed : will build a license limited version of the plugin
#

# Files to add to the plugin content (from the target directory)
# By default this is a directory containing the plugin jar
FILES += $(NAME)

include ../makefiles/common.mk

## For licensed plugins
# The path are expected on Rudder, and the license and key file will be provided separatly
# standard destination path for the license file is in module directory, "license.sign"
TARGET_LICENSE_PATH = /opt/rudder/etc/plugins/licenses/$(NAME).license
# standard destination path for the key:
TARGET_KEY_PATH = /opt/rudder/etc/plugins/licenses/license.key

plugins-common:
	cd ../plugins-common && make
	cd ../$(NAME)

plugins-common-nightly:
	cd ../plugins-common && make nightly
	cd ../$(NAME)

plugins-common-next:
	cd ../plugins-common && make next 
	cd ../$(NAME)

plugins-common-private:plugins-common
	cd ../plugins-common-private && make
	cd ../$(NAME)


plugins-common-private-nightly:plugins-common-nightly
	cd ../plugins-common-private && make nightly
	cd ../$(NAME)

plugins-common-private-next:plugins-common-next
	cd ../plugins-common-private && make next
	cd ../$(NAME)

build-files:   
	$(MVN_CMD) package
	mkdir -p target/$(NAME)
	mv target/$(NAME)-*-jar-with-dependencies.jar target/$(NAME)/$(NAME).jar

std-files: plugins-common$(LIB_SUFFIX) build-pom build-files

build-licensed-files:
	$(MVN_CMD) -Dlimited -Dplugin-resource-publickey=$(TARGET_KEY_PATH) -Dplugin-resource-license=$(TARGET_LICENSE_PATH) -Dplugin-declared-version=$(VERSION) package
	mkdir -p target/$(NAME)
	mv target/$(NAME)-*-jar-with-dependencies.jar target/$(NAME)/$(NAME).jar

licensed-files: plugins-common-private${LIB_SUFFIX} build-pom build-licensed-files

generate-pom: PLUGIN_POM_VERSION = $(RUDDER_VERSION)-${PLUGIN_VERSION}
generate-pom: RUDDER_BUILD_VERSION = $(RUDDER_VERSION)
generate-pom: RUDDER_POM_VERSION = $(RUDDER_VERSION)
generate-pom: build-pom

generate-pom-nightly: PLUGIN_POM_VERSION = $(RUDDER_VERSION)-${PLUGIN_VERSION}-SNAPSHOT
generate-pom-nightly: RUDDER_BUILD_VERSION = $(RUDDER_VERSION)
generate-pom-nightly: RUDDER_POM_VERSION = $(RUDDER_VERSION)
generate-pom-nightly: build-pom

generate-pom-next: PLUGIN_POM_VERSION = $(RUDDER_VERSION_NEXT)-${PLUGIN_VERSION}-SNAPSHOT
generate-pom-next: RUDDER_BUILD_VERSION = $(RUDDER_VERSION_NEXT)-SNAPSHOT
generate-pom-next: RUDDER_POM_VERSION = $(RUDDER_VERSION)
generate-pom-next: build-pom

build-pom:
	cp pom-template.xml pom.xml
	sed -i -e "s/\$${plugin-version}/${PLUGIN_POM_VERSION}/" pom.xml
	sed -i -e "s/\$${parent-version}/${RUDDER_POM_VERSION}-${COMMON_VERSION}/" pom.xml
	sed -i -e "s/\$${private-version}/${RUDDER_POM_VERSION}-${PRIVATE_VERSION}/" pom.xml
	sed -i -e "s/\$${rudder-version}/$(RUDDER_POM_VERSION)/" pom.xml
	sed -i -e "s/\$${rudder-build-version}/$(RUDDER_BUILD_VERSION)/" pom.xml


.PHONY: std-files licensed-files 
