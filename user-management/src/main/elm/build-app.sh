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
elm-${ELM_VER} make --optimize sources/UserManagement.elm --output=generated/UserManagement.js
