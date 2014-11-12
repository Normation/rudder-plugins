#####################################################################################
# Copyright 2014 Normation SAS
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

#===============================================================
# Specification file for rudder-plugin-external-node-information
#
# Rudder plugin for hello world
#
# Copyright (C) 2014 Normation
#===============================================================

#===============================================================
# Variables
#===============================================================
%define real_name        rudder-plugin-external-node-information

%define rudderdir        /opt/rudder
%define ruddervardir     /var/rudder
%define rudderlogdir     /var/log/rudder

#===============================================================
# Header
#===============================================================
Summary: Configuration management and audit tool - external node information plugin
Name: %{real_name}
Version: %{real_version}
Release: 1%{?dist}
Epoch: 1299256513
License: GPLv3
URL: http://www.rudder-project.org

Group: Applications/System

Source1: settings-external.xml
Source2: settings-internal.xml
Source3: external-node-information.properties

BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)
BuildArch: noarch

BuildRequires: jdk
Requires: rudder-server-root

%description
Rudder is an open source IT infrastructure automation and compliance solution.

This package contains a plugin for the main webapp (see rudder-webapp package)
that allows to add links to documents (typically HTML reports from third tier
applications) in a new tab in node details. 

#===============================================================
# Source preparation
#===============================================================
%prep

cp -rf %{_sourcedir}/rudder-sources %{_builddir}

#===============================================================
# Building
#===============================================================
%build

cd %{_builddir}/rudder-sources/rudder-parent-pom && %{_sourcedir}/maven2/bin/mvn -s %{SOURCE1} -Dmaven.test.skip=true install
cd %{_builddir}/rudder-sources/rudder-commons && %{_sourcedir}/maven2/bin/mvn -s %{SOURCE1} -Dmaven.test.skip=true install
cd %{_builddir}/rudder-sources/scala-ldap && %{_sourcedir}/maven2/bin/mvn -s %{SOURCE1} -Dmaven.test.skip=true install
cd %{_builddir}/rudder-sources/ldap-inventory && %{_sourcedir}/maven2/bin/mvn -s %{SOURCE1} -Dmaven.test.skip=true install
cd %{_builddir}/rudder-sources/cf-clerk && %{_sourcedir}/maven2/bin/mvn -s %{SOURCE1} -Dmaven.test.skip=true install
cd %{_builddir}/rudder-sources/rudder && %{_sourcedir}/maven2/bin/mvn -s %{SOURCE1} -Dmaven.test.skip=true install package
cd %{_builddir}/rudder-sources/rudder-plugin-external-node-information && %{_sourcedir}/maven2/bin/mvn -s %{SOURCE1} -Dmaven.test.skip=true install package

# Installation
#===============================================================
%install
rm -rf %{buildroot}

mkdir -p %{buildroot}%{rudderdir}/jetty7/rudder-plugins/
mkdir -p %{buildroot}%{rudderdir}/etc/plugins/

cp %{_builddir}/rudder-sources/rudder-plugin-external-node-information/target/external-node-information-*-plugin-with-own-dependencies.jar %{buildroot}%{rudderdir}/jetty7/rudder-plugins/external-node-information.jar
cp %{SOURCE3} %{buildroot}%{rudderdir}/etc/plugins/

%pre -n rudder-plugin-external-node-information
#===============================================================
# Pre Installation
#===============================================================

%post -n rudder-plugin-external-node-information
#===============================================================
# Post Installation
#===============================================================

# Do this ONLY at first install
if [ $1 -eq 1 ]
then
	echo "*******************************************************************************"
	echo "Rudder plugin 'External Node Information' is now installed but not initialized."
	echo "*******************************************************************************"
fi

echo "Please restart jetty to check plugins."

#===============================================================
# Cleaning
#===============================================================
%clean
rm -rf %{buildroot}
#==============================================================
# Files
#===============================================================
%files -n rudder-plugin-external-node-information
%defattr(-, root, root, 0755)
%{rudderdir}/jetty7/rudder-plugins/external-node-information.jar
%{rudderdir}/etc/plugins/external-node-information.properties

#===============================================================
# Changelog
#===============================================================
%changelog
* Wed, 12 Nov 2014 - Rudder packaging team <rudder-packaging@rudder-project.org> 2.11.4-alpha1

- Initial package

