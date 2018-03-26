# this file is an include which define general variable.
# It also parse all the variable in main-build.conf 
# and define variables accordingly


MAIN_BUILD          = $(shell  [ -f main-build.conf ] && echo '.' || echo '..')/main-build.conf
RUDDER_BRANCH       = $(shell sed -ne '/^rudder-branch=/s/rudder-branch=//p' $(MAIN_BUILD))
LIB_COMMON_VERSION  = $(shell sed -ne '/^lib-common=/s/lib-common=//p' $(MAIN_BUILD))
LIB_PRIVATE_VERSION = $(shell sed -ne '/^lib-common-private=/s/lib-common-private=//p' $(MAIN_BUILD))
PARENT_VERSION      = $(shell sed -ne '/^parent-plugin=/s/parent-plugin=//p' $(MAIN_BUILD))


ifneq (,$(wildcard ./build.conf))
PLUGIN_BRANCH   = $(shell sed -ne '/^plugin-branch=/s/plugin-branch=//p' build.conf)
VERSION         = $(RUDDER_BRANCH)-$(PLUGIN_BRANCH)
NAME            = $(shell sed -ne '/^plugin-name=/s/plugin-name=//p' build.conf)
FULL_NAME       = rudder-plugin-$(NAME)
endif

LIB_COMMON_NAME = plugins-common
LIB_PRIVATE_NAME = plugins-common-private

MAVEN_OPTS = --batch-mode 


generate-pom:
	cp pom-template.xml pom.xml
	sed -i -e "s/\$${rudder-branch}/$(RUDDER_BRANCH)/" pom.xml
	sed -i -e "s/\$${parent-version}/$(PARENT_VERSION)/" pom.xml
	sed -i -e "s/\$${lib-common}/$(LIB_COMMON_VERSION)/" pom.xml
	sed -i -e "s/\$${lib-common-private}/$(LIB_PRIVATE_VERSION)/" pom.xml
	sed -i -e "s/\$${plugin-version}/$(VERSION)/" pom.xml


