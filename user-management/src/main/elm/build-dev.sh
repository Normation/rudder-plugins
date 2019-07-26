#!/bin/sh

ELM_DIR="$( cd "$( dirname "$0" )" && pwd )"
cd $ELM_DIR

./build-app.sh
cp generated/* ../../../target/classes/toserve/usermanagement
cp ../resources/toserve/usermanagement/* ../../../target/classes/toserve/usermanagement