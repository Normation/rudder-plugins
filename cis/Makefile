#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#
#

REDHAT7_SHA256 = a9bbd16f5080d6993f31824f21276224640162eae12919fafe1491bec8262290
DEBIAN9_SHA256 = e3b2993054fdf0b64b27d33b29099c997e41a4766a42fdd460fe796d9d8b410c
UBUNTU18_SHA256 = c640dd4449ba2afa85056a00a7b8870ee8cf6978b39ec1e2072584f39507e36e

# Usage: $(GET) <destination_file> <url> <hash>
PROXY_ENV = $(if $(PROXY), http_proxy=$(PROXY) ftp_proxy=$(PROXY))
# No accept encoding to prevent the webserver form altering the distributed file
# Pass an empty string to skip hash check
# grep 'GET=' */SOURCES/Makefile to patch everywhere
GET=get() { $(PROXY_ENV) curl -s -L -o "$$1.part" "$$2" && { openssl dgst -sha256 "$$1.part" | grep -q "$$3" || { echo "Wrong checksum, aborting"; exit 1; }; } && mv "$$1.part" "$$1"; }; get

FILES = configuration-repository
SCRIPTS = postinst prerm cis.json rudder-synchronize
OTHER_ARCHIVES = target/configuration.txz

include ../makefiles/common-plugin.mk

configuration: target/configuration.txz

pdfs:
	mkdir -p pdfs
	$(GET) pdfs/cis_debian9.pdf https://publisher.normation.com/build-dependencies/cis/cis_debian9.pdf $(DEBIAN9_SHA256)
	$(GET) pdfs/cis_redhat7.pdf https://publisher.normation.com/build-dependencies/cis/cis_redhat7.pdf $(REDHAT7_SHA256)
	$(GET) pdfs/cis_ubuntu18.pdf https://publisher.normation.com/build-dependencies/cis/cis_ubuntu18.pdf $(UBUNTU18_SHA256)


target/configuration.txz: packaging/cis.json
	cp -ar configuration/rules target/
	cp -ar configuration/directives target/
	cp -ar configuration/techniques target/
	tar cJ -C target -f target/configuration.txz rules directives techniques

target/configuration-repository: configuration
	mkdir -p target/configuration-repository
	cp -ar configuration/ncf target/configuration-repository/

packaging/cis.json: pdfs
	tools/cis-tag

clean:
	rm -f  $(FULL_NAME)-*.rpkg pom.xml packaging/cis.json
	rm -rf target ncf
	rm -rf configuration/rules/*
	rm -rf pdfs
