# Make directive to build the two plugins-common and 
# plugins-common-private library. 

include ../makefiles/global-vars.mk

# define the lib name with that variable before include:
#LIB_TYPE = COMMON or PRIVATE


NAME = $(LIB_$(LIB_TYPE)_NAME)
VERSION = $(LIB_$(LIB_TYPE)_VERSION)

.DEFAULT_GOAL := std

# PUBLIC (plugins-common) is just the sbt parent settings now -> nothing to install.
# PRIVATE (plugins-common-private) is a real sbt module -> publish it to the local Maven repo,
# so licensed plugin builds can resolve it (needs the private license-lib from nexus.normation.com).
ifeq ($(LIB_TYPE),PRIVATE)
std:
	$(SBT_SHUTDOWN)
	cd .. && $(SBT_CMD) -Dlimited "plugins-common-private/publishM2"
	$(SBT_SHUTDOWN)
else
std:
	@echo "plugins-common is the sbt parent settings (no artifact to install)"
endif

clean:
	rm -f  pom.xml
	rm -rf target
