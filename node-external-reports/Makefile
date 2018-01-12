#
# make all will build node-external-reports.rpkg. 
# make demo will build a license limited version of the plugin
#

# You can provide your own configuration file by setting env var
# CONFIG_FILE_PATH with the path toward it. 

CONFIG_FILE_PATH = src/main/resources/node-external-reports.properties

FILES = $(NAME)/$(CONFIG_FILE_PATH)

SCRIPTS = postinst

include ../common-scala-plugin.mk

target/$(NAME)/$(CONFIG_FILE_PATH)
	cp $(CONFIG_FILE_PATH) target/$(NAME)/

