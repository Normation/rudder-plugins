#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = # add the file that need to be package here like $(NAME)/user-management-schema.sql
OTHER_TARGETS = target/$(NAME)/user-management-schema.sql
SCRIPTS = postinst

include ../makefiles/common-scala-plugin.mk

target/$(NAME)/user-management-schema.sql:
	cp ./src/main/resources/user-management-schema.sql target/$(NAME)/

