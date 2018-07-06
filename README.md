# rudder-plugins

This is the repository for plugins for Rudder: Continuous configuration for effective compliance.

https://www.rudder-project.org/site/documentation/

Repository structure
---------------------

The repository is organized with one directory for each plugin under repository root directory.

Each plugin's root directory is named with the plugin "shortname identifier", i.e the plugin name
minus 'rudder-plugin-" prefix.

Each plugin build information are grouped in file `build.conf` in plugin root directory.

Branch versionning and compability with Rudder versions
-------------------------------------------------------

Plugins are linked to Rudder main version, so we retrieve in `rudder-plugins` the same branch
structure than in `rudder`. Moreover, one needs to always compile and use a plugin for the
corresponding Rudder version:

```
- master (plugin compatible with Rudder next version, i.e developing branch)
- branches/rudder/3.1 (plugins compatible with Rudder 3.1)
- branches/rudder/4.1 (plugins compatible with Rudder 4.1)
- etc
```

This branch scheme allows to accomodate API changes between main Rudder versions.

Most of the time, there is no need to recompile a plugin for the corresponding Rudder minor version, so that
plugins compiled on branch `branches/rudder/4.1` should be compatible with all Rudder 4.1.x versions.

It may happens that at some point in the Rudder maintenance cycle, a Rudder minor version introduces a
breaking change in a plugin API or a binary incompability in a plugin ABI. In such a case, we will
explain which plugin versions are compatible with which Rudder versions in plugin readme file.

Plugin version and Tag convention
---------------------------------

Plugin versions are composed in two parts separated by a `-`:

- the Rudder corresponding version (without the minor number),
- the plugin own version in format X.Y(.Z) where the Z part is optionnal.

For example, the `datasources` plugin, in own version 1.1, for Rudder 4.1 will get version: `4.1-1.1`.

This version is used to postfix plugin package name.

Each plugin follow his own development pace, and so there is no release cycle for plugins. Each time a plugin
reaches a new step, a version for it is published by changing version information in its `build.conf` file.
The related commit is tagged with the convention: `pluginShortName-pluginVersion`.

You can get all the versions for a given plugins with the `git tag --list` command. For example, for the `datasources` plugin:

```
$ git tag --list 'datasources-*'

# results
datasources-4.1-0.1
datasources-4.1-0.2
datasources-4.1-1.0
datasources-4.1-1.1
datasources-4.2-1.1

```


Building and Java stack requirements
-------------------------------------

All plugins share the same build infrastructure based on Make.

You will need:

- standard `make` tool chain,
- `ar`, and for any plugin with scala code (i.e most of them),
- `maven` in version 3.2 or up,
- `Java 8 JDK tools (javac, jar, etc).

For information, this the list of package that need to be installed on a minimal linux distribution:

```
openjdk-8-jdk maven binutils make git-core xz-utils
```

For the branding plugin, you need to have elm-install present on the system

```
npm install -g  elm
```

To build a plugin package, do:

```
git checkout tag-corresponding-to-plugin-vesion
make clean && make generate-all-pom && make plugin-name
```

After compilation, you will find in plugin root directory (i.e at the same level than the Makefile file) the
plugin package: `pluginShortName-pluginVersion.rpkg`.

This package can then be transfered to a Rudder server and installed with the command:

```
/opt/rudder/bin/rudder-pkg install-file /path/to/pluginShortName-pluginVersion.rpkg
```

Building licensed / limited plugin version
------------------------------------------

As of Rudder 4.1, plugins can have a license and adapt there behavior based on runtime license information.
The licensing framework is not open source, and such plugin need access to Rudder private repositories.

The common API can be build and installed in user local maven repository with the following command line
when on `rudder-plugins` directory (for example for `datasources`, use the same `-licensed` naming 
convention for other):

```
make datasources-licensed SIGNED_LICENSE_PATH="./license.sign"  PUBLIC_KEY_PATH=/path/to/the/public/key.pub
```

The file `license.sign` is a license information file signed with the private key matching the public one
used in the command line. The path of `license.sign` is relative to the plugin directory, so in our example,
it will be located at: `./datasources/license.sign`

Licensing
---------

License are by-plugin and the license for a given plugin is specified in the LICENSE file in its plugin directory.
