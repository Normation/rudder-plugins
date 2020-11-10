# Make directive to build the two plugins-common and 
# plugins-common-private library. 

include ../makefiles/global-vars.mk

# define the lib name with that variable before include:
#LIB_TYPE = COMMON or PRIVATE


# maven local repo
MAVEN_LOCAL_REPO =  $(shell $(MVN_PARAMS) ../makefiles/find_m2_repo.sh)

NAME = $(LIB_$(LIB_TYPE)_NAME)
VERSION = $(BUILD_VERSION)-$(LIB_$(LIB_TYPE)_VERSION)
PLUGINS_JAR_PATH = $(MAVEN_LOCAL_REPO)/com/normation/plugins/$(NAME)/$(VERSION)/$(NAME)-$(VERSION).jar
PARENT_V = $(BUILD_VERSION)-$(PARENT_VERSION)
PARENT_POM = $(MAVEN_LOCAL_REPO)/com/normation/plugins/plugins-parent/$(PARENT_V)/plugins-parent-$(PARENT_V).pom

.DEFAULT_GOAL := generate-pom $(PLUGINS_JAR_PATH)

nightly: generate-pom-nightly $(PLUGINS_JAR_PATH)
# maven is such a pain that "mvn install" can't be used with parametrized version (it
# uses the literal '${plugin-version}' string in path). So we are doing installation by 
# hand. Yep, most beautiful tool.
$(PLUGINS_JAR_PATH): $(PARENT_POM)
	test -n "$(LIB_TYPE)"  # $$LIB_TYPE must be defined to COMMON or PRIVATE
	$(MVN_CMD) clean install

$(PARENT_POM):
	echo $(PARENT_POM)
	cd .. && $(MVN_CMD) install
clean:
	rm -f  pom.xml
	rm -rf target
