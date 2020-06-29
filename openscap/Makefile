# Usage: $(GET) <destination_file> <url> <hash>
PROXY_ENV = $(if $(PROXY), http_proxy=$(PROXY) ftp_proxy=$(PROXY))
# No accept encoding to prevent the webserver form altering the distributed file
# Pass an empty string to skip hash check
# grep 'GET=' */SOURCES/Makefile to patch everywhere
GET=get() { $(PROXY_ENV) curl -s -L -o "$$1.part" "$$2" && { openssl dgst -sha256 "$$1.part" | grep -q "$$3" || { echo "Wrong checksum, aborting"; exit 1; }; } && mv "$$1.part" "$$1"; }; get

FILES = remove_configuration openscap.json antisamy.xml openscap.properties techniques
SCRIPTS = postinst prerm

#include ../makefiles/common-plugin.mk
include ../makefiles/common-scala-plugin.mk

target/techniques:
	cp -r configuration/techniques target/techniques

target/remove_configuration:
	cp packaging/remove_configuration target/remove_configuration

target/openscap.json:
	tools/openscap

target/antisamy.xml:
	cp src/main/resources/antisamy.xml target/antisamy.xml

target/openscap.properties:
	cp src/main/resources/openscap.properties target/openscap.properties

clean:
	rm -f $(FULL_NAME)-*.rpkg pom.xml
	rm -rf target
