# this file is an include which define general variable.
# It also parse all the variable in main-build.conf 
# and define variables accordingly


MAIN_BUILD          = $(shell  [ -f main-build.conf ] && echo '.' || echo '..')/main-build.conf
RUDDER_BRANCH       = $(shell sed -ne '/^rudder-branch=/s/rudder-branch=//p' $(MAIN_BUILD))
LIB_COMMON_VERSION  = $(shell sed -ne '/^lib-common=/s/lib-common=//p' $(MAIN_BUILD))
LIB_PRIVATE_VERSION = $(shell sed -ne '/^lib-common-private=/s/lib-common-private=//p' $(MAIN_BUILD))

LIB_COMMON_NAME = plugins-common
LIB_PRIVATE_NAME = plugins-common-private

MAVEN_OPTS = --batch-mode -U -Drudder-branch="$(RUDDER_BRANCH)" -Dlib-common="$(LIB_COMMON_VERSION)" -Dlib-common-private="$(LIB_PRIVATE_VERSION)"

