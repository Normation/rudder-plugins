RUDDER_BRANCH = $(shell sed -ne '/^rudder-branch=/s/rudder-branch=//p' build.conf)
PLUGIN_BRANCH = $(shell sed -ne '/^plugin-branch=/s/plugin-branch=//p' build.conf)
VERSION = $(RUDDER_BRANCH)-$(PLUGIN_BRANCH)
NAME = $(shell sed -ne '/^plugin-id=/s/plugin-id=//p' build.conf)
MAVEN_OPTS = --batch-mode -U

all: $(NAME)-$(VERSION).rpkg

$(NAME)-$(VERSION).rpkg: target/metadata files.txz scripts.txz
	ar r $(NAME)-$(VERSION).rpkg target/metadata files.txz scripts.txz

target/metadata:
	mvn $(MAVEN_OPTS) -Dcommit-id=$$(git rev-parse HEAD 2>/dev/null || true) properties:read-project-properties resources:copy-resources@copy-metadata

files.txz: target/datasources.jar
	mkdir datasources
	mv target/datasources.jar datasources/
	cp ./src/main/resources/datasources-schema.sql datasources/
	tar cJ -f files.txz datasources

target/datasources.jar:
	mvn $(MAVEN_OPTS) package
	mv target/datasources-*-plugin-with-own-dependencies.jar target/datasources.jar

scripts.txz:
	tar cJ -C packaging -f scripts.txz postinst

clean:
	rm -f scripts.txz files.txz $(NAME)-$(VERSION).rpkg
	rm -rf target datasources
