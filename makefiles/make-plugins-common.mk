# Make directive to build the two plugins-common and 
# plugins-common-private library. 

include ../makefiles/common-scala-plugin.mk

# define the lib name with that variable before include:
#LIB_TYPE = COMMON or PRIVATE


# maven local repo
MAVEN_LOCAL_REPO =  $(shell $(MVN_PARAMS) ../makefiles/find_m2_repo.sh)

NAME = $(LIB_$(LIB_TYPE)_NAME)
VERSION = $(LIB_$(LIB_TYPE)_VERSION)
PLUGINS_JAR_PATH = $(MAVEN_LOCAL_REPO)/com/normation/plugins/$(NAME)/$(RUDDER_POM_VERSION)-$(LIB_$(LIB_TYPE)_VERSION)/$(RUDDER_POM_VERSION)-$(LIB_$(LIB_TYPE)_VERSION).jar

.DEFAULT_GOAL := std

std: RUDDER_BUILD_VERSION = $(RUDDER_VERSION)
std: RUDDER_POM_VERSION = $(RUDDER_VERSION)
std: PLUGINS_JAR_PATH = $(MAVEN_LOCAL_REPO)/com/normation/plugins/$(NAME)/$(RUDDER_POM_VERSION)-$(VERSION)/$(NAME)-$(RUDDER_POM_VERSION)-$(VERSION).pom
std: build-pom $(PLUGINS_JAR_PATH)

nightly: RUDDER_BUILD_VERSION = $(RUDDER_VERSION)
nightly: RUDDER_POM_VERSION = $(RUDDER_VERSION)
nightly: PLUGINS_JAR_PATH = $(MAVEN_LOCAL_REPO)/com/normation/plugins/$(NAME)/$(RUDDER_POM_VERSION)-$(VERSION)/$(NAME)-$(RUDDER_POM_VERSION)-$(VERSION).pom
nightly: build-pom $(PLUGINS_JAR_PATH)

next: RUDDER_BUILD_VERSION = $(RUDDER_VERSION_NEXT)-SNAPSHOT
next: RUDDER_POM_VERSION = $(RUDDER_VERSION_NEXT)
next: PLUGINS_JAR_PATH = $(MAVEN_LOCAL_REPO)/com/normation/plugins/$(NAME)/$(RUDDER_POM_VERSION)-$(LIB_$(LIB_TYPE)_VERSION)/$(NAME)-$(RUDDER_POM_VERSION)-$(LIB_$(LIB_TYPE)_VERSION).pom
next: build-pom $(PLUGINS_JAR_PATH)

# maven is such a pain that "mvn install" can't be used with parametrized version (it
# uses the literal '${plugin-version}' string in path). So we are doing installation by 
# hand. Yep, most beautiful tool.
$(PLUGINS_JAR_PATH):
	$(MVN_CMD) clean install

clean:
	rm -f  pom.xml
	rm -rf target
