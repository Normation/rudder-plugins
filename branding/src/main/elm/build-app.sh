#!/bin/bash

set -e

ELM_VER="0.19.1"

if ! command -v elm-${ELM_VER} &> /dev/null
then
  echo "# ERROR: missing elm-${ELM_VER} binary"
  echo "# To install the right compiler version:"
  echo ""
  echo "$ curl -L -o elm-${ELM_VER}.gz https://github.com/elm/compiler/releases/download/${ELM_VER}/binary-for-linux-64-bit.gz"
  echo "$ gzip -d elm-${ELM_VER}.gz"
  echo "$ chmod +x elm-${ELM_VER}"
  echo "# then put it somewhere in your PATH"
  exit 1
fi

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

elm-${ELM_VER} make --optimize sources/Branding.elm --output=generated/branding.js
