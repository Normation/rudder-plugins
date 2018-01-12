#
# make all : will build node-external-reports.rpkg. 
# make licensed : will build a license limited version of the plugin
#

FILES = $(NAME)/datasources-schema.sql

include ../makefiles/common-scala-plugin.mk

target/$(NAME)/datasources-schema.sql:
	cp ./src/main/resources/datasources-schema.sql target/$(NAME)/

