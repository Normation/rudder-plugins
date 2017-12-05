#
# make all : will build helloworld.rpkg. 
# make license : will build a license limited version of the plugin
#

# Use this one for a Scala plugin
include ../makefiles/common-scala-plugin.mk

# Uncomment this one for a non scala plugin
#include ../makefiles/common-plugin.mk

# Uncomment to add files to include in the plugin's main directory
# Those files are relative to the target/ directory
#FILES += $(NAME)/helloworld.properties
#target/$(NAME)/helloworld.properties:
	#cp src/main/resources/helloworld.properties target/$(NAME)/

# Uncomment to add scripts to the plugin (default is to have only postinst)
# Those files are relative to the packaging directory
#SCRIPTS += preinst

# Add another archive to the plugin (files.txz is the first one)
#OTHER_ARCHIVES += target/other.txz
#target/other.txz:
	#tar cJ -C packaging -f target/other.txz file1 file2

