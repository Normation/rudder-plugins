#####################################################################################
# Copyright 2011 Normation SAS
#####################################################################################
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, Version 3.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
#####################################################################################

MAVEN_RELEASE = 3.0.4
WGET := $(if $(PROXY), http_proxy=$(PROXY) ftp_proxy=$(PROXY)) /usr/bin/wget

.DEFAULT_GOAL := localdepends

GIT_BRANCH_RUDDER = <put branch or tag name here>
RUDDER_VERSION_TO_PACKAGE = <put Rudder version or version-snapshot here>

localdepends: ./rudder-sources ./maven2/bin/mvn ./rudder-sources/helloworld-plugin

/usr/bin/wget:
	sudo aptitude --assume-yes install wget

./maven.tgz: /usr/bin/wget
	# Original URL: http://apache.multidist.com/maven/binaries/apache-maven-$(MAVEN_RELEASE)-bin.tar.gz
	$(WGET) -O ./maven.tgz http://www.normation.com/tarball/apache-maven-$(MAVEN_RELEASE)-bin.tar.gz

./maven2/bin/mvn: ./maven.tgz
	tar -xvzf ./maven.tgz -C .
	rm -rf ./maven2
	mv ./apache-maven-$(MAVEN_RELEASE) ./maven2

./rudder-sources.tar.bz2:
	$(WGET) -O rudder-sources.tar.bz2 http://www.rudder-project.org/archives/rudder-sources-${RUDDER_VERSION_TO_PACKAGE}.tar.bz2

./rudder-sources: ./rudder-sources.tar.bz2
	tar -xjf rudder-sources.tar.bz2
	mv rudder-sources-*/ rudder-sources/

./rudder-sources/helloworld-plugin: ./rudder-sources
	mkdir -p ./rudder-sources/helloworld-plugin
	cp -a ../../src/ ./rudder-sources/helloworld-plugin/
	cp -a ../../pom.xml ./rudder-sources/helloworld-plugin

localclean:
	rm -rf ./rudder-sources
	rm -rf ./maven2/

veryclean:
	rm -f ./rudder-sources.tar.bz2
	rm -rf ./maven.tgz

.PHONY: localclean localdepends veryclean
