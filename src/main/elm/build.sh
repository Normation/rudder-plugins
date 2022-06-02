#!/bin/bash

# we want that all elm-stuff stay in src/main/elm
# whatever the path from which this script is called
export ELM_DIR="$( cd "$( dirname "$0" )" && pwd )"

. "${ELM_DIR}/../../../../plugins-common/elm.sh" AuthBackends "$@"
