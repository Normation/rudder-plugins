#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = $(NAME)/change-validation-schema.sql $(NAME)/change-validation.conf $(NAME)/validation-mail.template $(NAME)/deployment-mail.template $(NAME)/cancelled-mail.template $(NAME)/deployed-mail.template
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
