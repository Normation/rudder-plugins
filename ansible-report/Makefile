FILES = ncf techniques
SCRIPTS = postinst prerm

TECHNIQUES=$(shell ls -1 configuration-repository/ncf/50_techniques)

include ../makefiles/common-plugin.mk

target/ncf:
	cp -a configuration-repository/ncf target

target/techniques:
	cp -a configuration-repository/techniques target
	mkdir -p target/techniques/ncf_techniques
	for technique in "$(TECHNIQUES)";                                \
	do                                                               \
		cd target && mkdir -p techniques/ansible_report/$$technique && cd -;  \
		cd target/techniques/ncf_techniques && ln -rs ../ansible_report/$$technique $$techniques  && cd -;   \
	done;
