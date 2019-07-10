#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = $(NAME)/change-validation-schema.sql
SCRIPTS = postinst

include ../makefiles/common-scala-plugin.mk


target/$(NAME)/change-validation-schema.sql:
	cp ./src/main/resources/change-validation-schema.sql target/$(NAME)/
