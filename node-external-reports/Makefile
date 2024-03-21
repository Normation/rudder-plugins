#
# make all will build node-external-reports.rpkg.
# make demo will build a license limited version of the plugin
#

# You can provide your own configuration file by setting env var
# CONFIG_FILE_PATH with the path toward it.

CONFIG_FILE_NAME = node-external-reports.properties
CONFIG_FILE_PATH = src/main/resources/$(CONFIG_FILE_NAME)

FILES =
OTHER_TARGETS = target/$(NAME)/$(CONFIG_FILE_NAME)

SCRIPTS = postinst

include ../makefiles/common-scala-plugin.mk

target/$(NAME)/$(CONFIG_FILE_NAME):
	cp $(CONFIG_FILE_PATH) target/$(NAME)/$(CONFIG_FILE_NAME)

