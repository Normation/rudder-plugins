RUDDER_BRANCH = $(shell sed -ne '/^rudder-branch=/s/rudder-branch=//p' build.conf)
PLUGIN_BRANCH = $(shell sed -ne '/^plugin-branch=/s/plugin-branch=//p' build.conf)
VERSION = $(RUDDER_BRANCH)-$(PLUGIN_BRANCH)
MAVEN_OPTS = --batch-mode -U

all: package-$(VERSION).rpkg

package-$(VERSION).rpkg: target/metadata files.txz scripts.txz
	ar r package-$(VERSION).rpkg target/metadata files.txz scripts.txz

target/metadata:
	mvn $(MAVEN_OPTS) -Dcommit-id=$$(git rev-parse HEAD 2>/dev/null || true) properties:read-project-properties resources:copy-resources@copy-metadata

files.txz: target/datasources.jar
	mkdir datasources
	mv target/datasources.jar datasources/
	cp ./src/main/resources/datasource-schema.sql datasources/
	tar cJ -f files.txz datasources

target/datasources.jar:
	mvn $(MAVEN_OPTS) package
	mv target/datasources-*-plugin-with-own-dependencies.jar target/datasources.jar

scripts.txz:
	tar cJ -C packaging -f scripts.txz postinst

clean:
	rm -f scripts.txz files.txz package-version.rpkg
	rm -rf target datasources
