RUDDER_SYNCHRONIZE_SHA256 = fcfd9ea8c574c120424f16b661f8f75696a410e243ee030a79dc8dd3743e6f42

# Usage: $(GET) <destination_file> <url> <hash>
PROXY_ENV = $(if $(PROXY), http_proxy=$(PROXY) ftp_proxy=$(PROXY))
# No accept encoding to prevent the webserver form altering the distributed file
# Pass an empty string to skip hash check
# grep 'GET=' */SOURCES/Makefile to patch everywhere
GET=get() { $(PROXY_ENV) curl -s -L -o "$$1.part" "$$2" && { openssl dgst -sha256 "$$1.part" | grep -q "$$3" || { echo "Wrong checksum, aborting"; exit 1; }; } && mv "$$1.part" "$$1"; }; get

FILES = rudder-synchronize ansible-report.json techniques ansible.sh
SCRIPTS = postinst prerm

include ../makefiles/common-plugin.mk


target/ansible-report.json:
	tools/ansible-report

target/ansible.sh:
	cp tools/ansible.sh target/ansible.sh

target/rudder-synchronize:
	wget https://repository.rudder.io/tools/rudder-synchronize -O target/rudder-synchronize

target/techniques:
	cp -R configuration/* target/

clean:
	rm -f $(FULL_NAME)-*.rpkg pom.xml packaging/ansible-report.json
	rm -rf target

