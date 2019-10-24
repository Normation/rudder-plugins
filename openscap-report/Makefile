FILES = ncf techniques
SCRIPTS = postinst prerm
OTHER_ARCHIVES = target/external-report.txz

TECHNIQUES=$(shell ls -1 configuration-repository/ncf/50_techniques)

include ../makefiles/common-plugin.mk

target/ncf:
	cp -a configuration-repository/ncf target

target/techniques:
	cp -a configuration-repository/techniques target
	mkdir -p target/techniques/ncf_techniques
	for technique in "$(TECHNIQUES)";                                \
	do                                                               \
		cd target && mkdir -p techniques/plugin_openscap_report/$$technique && cd -;  \
		cd target/techniques/ncf_techniques && ln -rs ../plugin_openscap_report/$$technique $$techniques  && cd -;   \
	done;

target/external-report.txz:
	cp -a src/node-external-reports.properties target/
	tar cJ -C target -f target/external-report.txz node-external-reports.properties

