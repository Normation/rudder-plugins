#!/bin/bash

# we want that all elm-stuff stay in src/main/elm
# whatever the path from which this script is called
ELM_DIR="$( cd "$( dirname "$0" )" && pwd )"
cd $ELM_DIR

# Only call elm-install if elm-stuff directory does not exists
# we don't need to it at every build, only if dependencies change
# And in most case, elm-make will treat them correctly
# If you need a dependency that cannot be installed with elm-make (ie elm-ui)
# Please for now delete elm-stuff file
# On CI, since we clean repository before build, elm-stuff will not exist
if [ ! -e elm-stuff ]; then
  elm-install
fi
elm-make sources/Branding.elm --output=generated/branding.js --yes
