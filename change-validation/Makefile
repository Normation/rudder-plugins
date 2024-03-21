#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES =
OTHER_TARGETS = target/$(NAME)/change-validation-schema.sql target/$(NAME)/change-validation.conf target/$(NAME)/validation-mail.template target/$(NAME)/deployment-mail.template target/$(NAME)/cancelled-mail.template target/$(NAME)/deployed-mail.template
SCRIPTS = postinst

include ../makefiles/common-scala-plugin.mk


target/$(NAME)/change-validation-schema.sql:
	cp ./src/main/resources/change-validation-schema.sql target/$(NAME)/
target/$(NAME)/change-validation.conf:
	cp ./src/main/resources/change-validation.conf target/$(NAME)/
target/$(NAME)/validation-mail.template:
	cp ./src/main/resources/validation-mail.template target/$(NAME)/
target/$(NAME)/deployment-mail.template:
	cp ./src/main/resources/deployment-mail.template target/$(NAME)/
target/$(NAME)/deployed-mail.template:
	cp ./src/main/resources/deployed-mail.template target/$(NAME)/
target/$(NAME)/cancelled-mail.template:
	cp ./src/main/resources/cancelled-mail.template target/$(NAME)/
