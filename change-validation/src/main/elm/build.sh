#!/bin/bash

# we want that all elm-stuff stay in src/main/elm
# whatever the path from which this script is called
export ELM_DIR="$( cd "$( dirname "$0" )" && pwd )"

. ../../../../plugins-common/elm.sh SupervisedTargets "$@"
. ../../../../plugins-common/elm.sh WorkflowUsers "$@"
