#!/bin/bash

BASE=/home/fanf/java/workspaces/rudder-project/rudder-plugins/api-authorizations
$BASE/src/main/elm/build-app.sh && \
mkdir -p $BASE/target/classes/toserve/api-authorizations
cp $BASE/src/main/elm/generated/*.js $BASE/target/classes/toserve/api-authorizations
cp $BASE/src/main/elm/toserve/* $BASE/target/classes/toserve/api-authorizations
#cp $BASE/src/main/elm/reportingManagement.html $BASE/target/classes/template/reportingManagement.html
echo "[$(date -Is)] Elm compilation done"
