# Make directive to build the two plugins-common and 
# plugins-common-private library. 

include ../makefiles/global-vars.mk

# define the lib name with that variable before include:
#LIB_TYPE = COMMON or PRIVATE


# maven local repo
MAVEN_LOCAL_REPO =  $(shell $(MVN_PARAMS) ../makefiles/find_m2_repo.sh)

NAME = $(LIB_$(LIB_TYPE)_NAME)
VERSION = $(LIB_$(LIB_TYPE)_VERSION)
#PLUGINS_JAR_PATH = $(MAVEN_LOCAL_REPO)/com/normation/plugins/$(NAME)/$(RUDDER_POM_VERSION)-$(LIB_$(LIB_TYPE)_VERSION)/$(RUDDER_POM_VERSION)-$(LIB_$(LIB_TYPE)_VERSION).jar
PLUGINS_JAR_PATH = $(MAVEN_LOCAL_REPO)/com/normation/plugins/$(NAME)/$(RUDDER_POM_VERSION)-$(VERSION)/$(NAME)-$(RUDDER_POM_VERSION)-$(VERSION).pom

.DEFAULT_GOAL := std

std: generate-pom $(PLUGINS_JAR_PATH)

# maven is such a pain that "mvn install" can't be used with parametrized version (it
# uses the literal '${plugin-version}' string in path). So we are doing installation by 
# hand. Yep, most beautiful tool.
$(PLUGINS_JAR_PATH):
	$(MVN_CMD) clean install

clean:
	rm -f  pom.xml
	rm -rf target
