#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = $(NAME)/server-node-to-relay
SCRIPTS = postinst

include ../makefiles/common-scala-plugin.mk

target/$(NAME)/server-node-to-relay:
	cp ./src/main/resources/server-node-to-relay target/$(NAME)/
