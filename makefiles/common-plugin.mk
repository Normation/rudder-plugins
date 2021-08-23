# Common make directives for all plugins not based on a jar. 
# make all : will build $(NAME).rpkg. 
# make licensed : is not yet supported
#

include ../makefiles/common.mk

std-files:

licensed-files:
	echo "License not supported for this kind of plugin, building without license"


