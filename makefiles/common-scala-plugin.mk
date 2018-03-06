# Common make directives for all plugins based on a jar. 
# make all : will build $(NAME).rpkg. 
# make licensed : will build a license limited version of the plugin
#

# Files to add to the plugin content (from the target directory)
# By default this is a directory containing the plugin jar
FILES += $(NAME)

include ../makefiles/common.mk

MAVEN_OPTS = --batch-mode -U

## For licensed plugins
# standard destination path for the license file is in module directory, "license.sign"
TARGET_LICENSE_PATH = /opt/rudder/share/plugins/$(NAME)/license
# standard destination path for the key is at JAR root, name: license.pubkey
TARGET_KEY_CLASSPATH = license.pubkey
# SIGNED_LICENSE_PATH: path towards the license file to embed
# PUBLIC_KEY_PATH: path towards the public key to embed

std-files: 
	mvn $(MAVEN_OPTS) package
	mkdir -p target/$(NAME)
	mv target/$(NAME)-*-jar-with-dependencies.jar target/$(NAME)/$(NAME).jar

licensed-files: check-license 
	mvn $(MAVEN_OPTS) -Dlimited -Dplugin-resource-publickey=$(TARGET_KEY_CLASSPATH) -Dplugin-resource-license=$(TARGET_LICENSE_PATH) -Dplugin-declared-version=$(VERSION) package
	mkdir -p target/$(NAME)
	mv target/$(NAME)-*-jar-with-dependencies.jar target/$(NAME)/$(NAME).jar
	cp $(PUBLIC_KEY_PATH) target/$(TARGET_KEY_CLASSPATH)
	jar -uf target/$(NAME)/$(NAME).jar -C target $(TARGET_KEY_CLASSPATH)
	# embed the license
	cp $(SIGNED_LICENSE_PATH) target/$(NAME)/license

check-license: 
	test -n "$(SIGNED_LICENSE_PATH)"  # $$SIGNED_LICENSE_PATH must be defined
	test -n "$(PUBLIC_KEY_PATH)"      # $$PUBLIC_KEY_PATH must be defined

.PHONY: std-files licensed-files check-license
