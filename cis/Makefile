#
# make all : will build node-external-reports.rpkg.
# make licensed : will build a license limited version of the plugin
#

FILES = ncf
SCRIPTS = postinst prerm cis.json rudder-synchronize
OTHER_ARCHIVES = target/configuration.txz
CIS_REDHAT_SHA256 = a9bbd16f5080d6993f31824f21276224640162eae12919fafe1491bec8262290

GET=get() { $(PROXY_ENV) curl -s -L -o "$$1.part" "$$2" && { openssl dgst -sha256 "$$1.part" | grep -q "$$3" || { echo "Wrong checksum, aborting"; exit 1; }; } && mv "$$1.part" "$$1"; }; get

include ../makefiles/common-plugin.mk
targets = $(addprefix target/, $(FILES))

$(targets): target/%: % pdfs/cis_redhat.pdf target/configuration.txz
	cp -ar $< target/

configuration: target/configuration.txz

target/configuration.txz:
	cp -ar configuration/rules target/
	cp -ar configuration/directives target/
	cp -ar configuration/techniques target/
	tar cJ -C target -f target/configuration.txz rules directives techniques

ncf:
	cp -ar configuration/ncf .

pdfs/cis_redhat.pdf:
	$(GET) pdfs/cis_redhat.pdf http://www.itsecure.hu/library/image/CIS_Red_Hat_Enterprise_Linux_7_Benchmark_v2.1.1.pdf $(CIS_REDHAT_SHA256)

packaging/cis.json:
	tools/cis-tag

clean:
	rm -f  $(FULL_NAME)-*.rpkg pom.xml packaging/cis.json
	rm -rf target ncf
	rm -rf pdfs/*.pdf
