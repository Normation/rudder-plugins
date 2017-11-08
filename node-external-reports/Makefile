#
# make all will build node-external-reports.rpkg. 
# make demo will build a license limited version of the plugin
#

include ../common-scala-plugin.mk

# You can provide your own configuration file by setting env var
# CONFIG_FILE_PATH with the path toward it. 

CONFIG_FILE_PATH = src/main/resources/node-external-reports.properties

plugin: all

plugin-resources:
	cp $(CONFIG_FILE_PATH) target/$(NAME)/


