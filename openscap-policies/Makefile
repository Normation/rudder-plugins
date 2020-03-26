# Usage: $(GET) <destination_file> <url> <hash>
PROXY_ENV = $(if $(PROXY), http_proxy=$(PROXY) ftp_proxy=$(PROXY))
# No accept encoding to prevent the webserver form altering the distributed file
# Pass an empty string to skip hash check
# grep 'GET=' */SOURCES/Makefile to patch everywhere
GET=get() { $(PROXY_ENV) curl -s -L -o "$$1.part" "$$2" && { openssl dgst -sha256 "$$1.part" | grep -q "$$3" || { echo "Wrong checksum, aborting"; exit 1; }; } && mv "$$1.part" "$$1"; }; get

RUDDER_SYNCHRONIZE_SHA256 = e0ba357886ac4b5c6a5856db191371922685a981af0c06596bd4adb8bcbefda1
FILES = remove_configuration rudder_synchronize.py openscap-policies.json techniques
SCRIPTS = postinst prerm
OTHER_ARCHIVES = target/external-report.txz

include ../makefiles/common-scala-plugin.mk

target/techniques:
	cp -r configuration/techniques target/techniques

target/remove_configuration:
	cp packaging/remove_configuration target/remove_configuration

target/openscap-policies.json:
	tools/openscap-policies

target/rudder_synchronize.py:
	$(GET) target/rudder_synchronize.py https://repository.rudder.io/tools/rudder-synchronize $(RUDDER_SYNCHRONIZE_SHA256)

target/external-report.txz:
	cp -a src/node-external-reports.properties target/
	tar cJ -C target -f target/external-report.txz node-external-reports.properties

clean:
	rm -f $(FULL_NAME)-*.rpkg pom.xml
	rm -rf target
