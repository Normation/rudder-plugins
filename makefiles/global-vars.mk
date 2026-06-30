# this file is an include which define general variable.
# It also parse all the variable in main-build.conf 
# and define variables accordingly

MAIN_BUILD      = $(shell  [ -f main-build.conf ] && echo '.' || echo '..')/main-build.conf
RUDDER_VERSION  = $(shell sed -ne '/^rudder-version=/s/rudder-version=//p' $(MAIN_BUILD))
MINOR_VERSION   = $(shell echo "$(RUDDER_VERSION)" | grep -Po '^\d+.\d+')
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
MVN_CMD = mvn $(MVN_PARAMS) --batch-mode -Djansi.passthrough=true -Dstyle.color=always

# sbt build (replaces maven). Invoked from the repo root (where build.sbt lives). A UTF-8 locale
# is required (assembly fails on non-ASCII classfile names otherwise). Extra args via SBT_PARAMS.
# Used as `cd .. && $(SBT_CMD) <-D opts> "<task>"`. Runs through the native client (sbtn) for fast
# start-up — we do NOT pass --server (that would boot a full foreground JVM and defeat the quick start).
SBT_CMD = LANG=C.UTF-8 LC_ALL=C.UTF-8 sbt -batch $(SBT_PARAMS)

# Shut the sbt server down. Run as its OWN recipe line both BEFORE a build (so this invocation's -D —
# incl. -Dlimited / -Dplugin-resource-* — take effect on a fresh server: sbt 2.0 binds -D at server
# startup, and the sbtn quick-start client otherwise reuses a running server with stale -D) and AFTER it
# (leave a clean state, no lingering server). Output is discarded on purpose: shutting the server down
# makes the sbtn native client print a benign `ArrayIndexOutOfBoundsException` (a GraalVM-native-image
# JNI race in ipcsocket while reading the socket the server is closing) — harmless noise, exit code is
# fine, so we send it to /dev/null instead of switching off the quick-start client. `--no-server` keeps
# it from spawning a JVM just to discover there is no server to stop (fast no-op when already clean).
SBT_SHUTDOWN = cd .. && (LANG=C.UTF-8 LC_ALL=C.UTF-8 sbt --no-server -batch shutdown >/dev/null 2>&1 || true)

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
