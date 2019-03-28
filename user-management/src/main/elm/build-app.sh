#!/bin/bash

# we want that all elm-stuff stay in src/main/elm
# whatever the path from which this script is called
ELM_DIR="$( cd "$( dirname "$0" )" && pwd )"
cd $ELM_DIR
elm make sources/user-management.elm --output=generated/user-management.js
