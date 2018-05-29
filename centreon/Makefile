#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = bin share etc
SCRIPTS = postinst

include ../makefiles/common-plugin.mk
targets = $(addprefix target/, $(FILES))

$(targets): target/%: %
	    cp -a $< target/
