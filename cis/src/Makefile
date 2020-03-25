.PHONY: ubuntu18 debian9 redhat7
.DEFAULT_GOAL := target

REDHAT7_SHA256 = a9bbd16f5080d6993f31824f21276224640162eae12919fafe1491bec8262290
DEBIAN9_SHA256 = e3b2993054fdf0b64b27d33b29099c997e41a4766a42fdd460fe796d9d8b410c
UBUNTU18_SHA256 = c640dd4449ba2afa85056a00a7b8870ee8cf6978b39ec1e2072584f39507e36e

# Usage: $(GET) <destination_file> <url> <hash>
PROXY_ENV = $(if $(PROXY), http_proxy=$(PROXY) ftp_proxy=$(PROXY))
# No accept encoding to prevent the webserver form altering the distributed file
# Pass an empty string to skip hash check
# grep 'GET=' */SOURCES/Makefile to patch everywhere
GET=get() { $(PROXY_ENV) curl -s -L -o "$$1.part" "$$2" && { openssl dgst -sha256 "$$1.part" | grep -q "$$3" || { echo "Wrong checksum, aborting"; exit 1; }; } && mv "$$1.part" "$$1"; }; get

DESTDIR = $(CURDIR)/target

pdfs:
	mkdir -p pdfs
	$(GET) pdfs/cis_debian9.pdf https://publisher.normation.com/build-dependencies/cis/cis_debian9.pdf $(DEBIAN9_SHA256)
	$(GET) pdfs/cis_redhat7.pdf https://publisher.normation.com/build-dependencies/cis/cis_redhat7.pdf $(REDHAT7_SHA256)
	$(GET) pdfs/cis_ubuntu18.pdf https://publisher.normation.com/build-dependencies/cis/cis_ubuntu18.pdf $(UBUNTU18_SHA256)

target/configuration/ncf:
	mkdir -p $(DESTDIR)/configuration
	cp -r ncf $(DESTDIR)/configuration/

ubuntu18: pdfs target/configuration/ncf
	mkdir -p $(DESTDIR)
	./tools/cisTag.py ubuntu18
	./tools/makeRule.py ubuntu18/ pdfs/cis_ubuntu18.pdf ubuntu18
	cp -ar ubuntu18/techniques target/configuration

debian9: pdfs target/configuration/ncf
	mkdir -p $(DESTDIR)
	./tools/cisTag.py debian9
	./tools/makeRule.py debian9/ pdfs/cis_debian9.pdf debian9
	cp -ar debian9/techniques target/configuration

redhat7: pdfs target/configuration/ncf
	mkdir -p $(DESTDIR)
	./tools/cisTag.py redhat7
	./tools/makeRule.py redhat7/ pdfs/cis_redhat7.pdf redhat7
	cp -ar redhat7/techniques target/configuration

clean:
	./tools/clean.py
	rm -rf target

