#!/bin/sh

ELM_DIR="$( cd "$( dirname "$0" )" && pwd )"
cd $ELM_DIR

./build-app.sh
cp generated/* ../../../target/classes/toserve/changevalidation
cp ../resources/toserve/changevalidation/* ../../../target/classes/toserve/changevalidation