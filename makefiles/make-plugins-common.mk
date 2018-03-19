# Make directive to build the two plugins-common and 
# plugins-common-private library. 

# Files to add to the plugin content (from the target directory)
# By default this is a directory containing the plugin jar
FILES += $(NAME)

include ../makefiles/common.mk

# maven local repo

MAVEN_OPTS = --batch-mode -U

MAVEN_LOCAL_REPO =  $(shell mvn help:effective-settings | grep localRepository | sed -e "s/.*>\(.*\)<.*/\1/")
PLUGINS_JAR_PATH = $(MAVEN_LOCAL_REPO)/com/normation/plugins/$(NAME)/$(VERSION)/$(NAME)-$(VERSION).jar

.DEFAULT_GOAL := $(PLUGINS_JAR_PATH)

# maven is such a pain that "mvn install" can't be used with parametrized version (it
# uses the literal '${plugin-version}' string in path). So we are doing installation by 
# hand. Yep, most beautiful tool.
$(PLUGINS_JAR_PATH):
	mvn clean package && mvn install:install-file -Dfile=target/$(NAME)-$(VERSION).jar \
                         -DpomFile=pom.xml \
                         -DgroupId=com.normation.plugins \
                         -DartifactId=$(NAME) \
                         -Dversion=$(VERSION) \
                         -Dpackaging=jar \
                         -DcreateChecksum=true

 
