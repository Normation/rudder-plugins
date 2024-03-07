# this file is an include which define general variable.
# It also parse all the variable in main-build.conf 
# and define variables accordingly

MAIN_BUILD      = $(shell  [ -f main-build.conf ] && echo '.' || echo '..')/main-build.conf
RUDDER_VERSION  = $(shell sed -ne '/^rudder-version=/s/rudder-version=//p' $(MAIN_BUILD))
BRANCH_TYPE     = $(shell sed -ne '/^branch-type=/s/branch-type=//p' $(MAIN_BUILD))
COMMON_VERSION  = $(shell sed -ne '/^common-version=/s/common-version=//p' $(MAIN_BUILD))
PRIVATE_VERSION = $(shell sed -ne '/^private-version=/s/private-version=//p' $(MAIN_BUILD))

ifneq (,$(wildcard ./build.conf))
PLUGIN_VERSION   = $(shell sed -ne '/^plugin-version=/s/plugin-version=//p' build.conf)
NAME            = $(shell sed -ne '/^plugin-name=/s/plugin-name=//p' build.conf)
FULL_NAME       = rudder-plugin-$(NAME)
endif

LIB_PRIVATE_NAME = plugins-common-private
LIB_PUBLIC_NAME = plugins-parent
LIB_PRIVATE_VERSION = ${PRIVATE_VERSION}
LIB_PUBLIC_VERSION = ${COMMON_VERSION}

VERSION = ${RUDDER_VERSION}-${PLUGIN_VERSION}
PLUGIN_POM_VERSION = $(RUDDER_VERSION)-${PLUGIN_VERSION}
RUDDER_BUILD_VERSION = $(RUDDER_VERSION)
ifeq ($(BRANCH_TYPE),nightly)
VERSION = ${RUDDER_VERSION}-${PLUGIN_VERSION}-nightly
PLUGIN_POM_VERSION = $(RUDDER_VERSION)-${PLUGIN_VERSION}-SNAPSHOT
endif
ifeq ($(BRANCH_TYPE),next)
PLUGIN_POM_VERSION = $(RUDDER_VERSION)-${PLUGIN_VERSION}-SNAPSHOT
VERSION = ${RUDDER_VERSION}-${PLUGIN_VERSION}-nightly
RUDDER_BUILD_VERSION = $(RUDDER_VERSION)-SNAPSHOT
endif

# if you want to add maven command line parameter, add them with MVN_PARAMS
MVN_CMD = mvn $(MVN_PARAMS) --batch-mode

RANDOM := $(shell bash -c 'echo $$RANDOM')

generate-pom:
	# avoid race condition in plugins-common when building plugin in parallel
	cp pom-template.xml pom.xml.$(RANDOM)
	sed -i -e "s/\$${plugin-version}/${PLUGIN_POM_VERSION}/" pom.xml.$(RANDOM)
	sed -i -e "s/\$${parent-version}/${RUDDER_VERSION}-${COMMON_VERSION}/" pom.xml.$(RANDOM)
	sed -i -e "s/\$${private-version}/${RUDDER_VERSION}-${PRIVATE_VERSION}/" pom.xml.$(RANDOM)
	sed -i -e "s/\$${rudder-version}/$(RUDDER_VERSION)/" pom.xml.$(RANDOM)
	sed -i -e "s/\$${rudder-build-version}/$(RUDDER_BUILD_VERSION)/" pom.xml.$(RANDOM)
	mv pom.xml.$(RANDOM) pom.xml
