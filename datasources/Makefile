#
# make all : will build node-external-reports.rpkg. 
# make licensed : will build a license limited version of the plugin
#

include ../makefiles/common-scala-plugin.mk

FILES += $(NAME)/datasources-schema.sql

target/$(NAME)/datasources-schema.sql:
	cp ./src/main/resources/datasources-schema.sql target/$(NAME)/

