# Usage: $(GET) <destination_file> <url> <hash>
PROXY_ENV = $(if $(PROXY), http_proxy=$(PROXY) ftp_proxy=$(PROXY))
# No accept encoding to prevent the webserver form altering the distributed file
# Pass an empty string to skip hash check
# grep 'GET=' */SOURCES/Makefile to patch everywhere
GET=get() { $(PROXY_ENV) curl -s -L -o "$$1.part" "$$2" && { openssl dgst -sha256 "$$1.part" | grep -q "$$3" || { echo "Wrong checksum, aborting"; exit 1; }; } && mv "$$1.part" "$$1"; }; get

RUDDER_SYNCHRONIZE_SHA256 = ccdc4f55af90a96c50caac639b19e2a1717cded9e0fbdd3fa7ff1c2d0208e64d
FILES = remove-configuration rudder-synchronize openscap-policies.json techniques
SCRIPTS = postinst prerm
OTHER_ARCHIVES = target/external-report.txz

include ../makefiles/common-plugin.mk

target/techniques:
	cp -r configuration/techniques target/techniques

target/remove-configuration:
	cp packaging/remove-configuration target/remove-configuration

target/openscap-policies.json:
	tools/openscap-policies

target/rudder-synchronize:
	$(GET) target/rudder-synchronize https://repository.rudder.io/tools/rudder-synchronize $(RUDDER_SYNCHRONIZE_SHA256)

target/external-report.txz:
	cp -a src/node-external-reports.properties target/
	tar cJ -C target -f target/external-report.txz node-external-reports.properties

clean:
	rm -f $(FULL_NAME)-*.rpkg pom.xml
	rm -rf target
