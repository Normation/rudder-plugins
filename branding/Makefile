#
# make all : will build node-external-reports.rpkg. 
# make licensed : will build a license limited version of the plugin
#

FILES = $(NAME)/reporting-schema.sql
SCRIPTS = postinst

include ../makefiles/common-scala-plugin.mk

target/$(NAME)/reporting-schema.sql:
	cp ./src/main/resources/reporting-schema.sql target/$(NAME)/

