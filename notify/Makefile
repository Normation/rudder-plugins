#
# make all : will build .rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = bin/ share/
SCRIPTS = postinst

include ../makefiles/common-plugin.mk
targets = $(addprefix target/, $(FILES))

$(targets): target/%: %
	    cp -a $< target/
