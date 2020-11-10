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

plugins-common-private:
	cd ../plugins-common-private && make
	cd ../$(NAME)

plugins-common-nightly:
	cd ../plugins-common && make nightly
	cd ../$(NAME)

plugins-common-private-nightly:
	cd ../plugins-common-private && make nightly
	cd ../$(NAME)

build-files:   
	$(MVN_CMD) package
	mkdir -p target/$(NAME)
	mv target/$(NAME)-*-jar-with-dependencies.jar target/$(NAME)/$(NAME).jar

std-files: plugins-common generate-pom build-files

std-files-nightly: plugins-common-nightly generate-pom-nightly build-files

build-licensed-files:
	$(MVN_CMD) -Dlimited -Dplugin-resource-publickey=$(TARGET_KEY_PATH) -Dplugin-resource-license=$(TARGET_LICENSE_PATH) -Dplugin-declared-version=$(VERSION) package
	mkdir -p target/$(NAME)
	mv target/$(NAME)-*-jar-with-dependencies.jar target/$(NAME)/$(NAME).jar

licensed-files: plugins-common plugins-common-private generate-pom build-licensed-files

licensed-files-nightly:  plugins-common-nightly plugins-common-private-nightly generate-pom-nightly build-licensed-files

.PHONY: std-files licensed-files 
