#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = # add the file that need to be package here like $(NAME)/auth-backends-schema.sql
SCRIPTS = postinst

include ../makefiles/common-scala-plugin.mk

target/$(NAME)/auth-backends-schema.sql:
	cp ./src/main/resources/auth-backends-schema.sql target/$(NAME)/

