#!/bin/bash

# Lib for building elm projects

# Similar to https://github.com/Normation/rudder/blob/master/webapp/sources/rudder/rudder-web/src/main/elm/build.sh
# Should be kept in sync

# Call without option for a dev build
# Call with --release for an optimized and minified build

# The argument is the name of the elm source to compile
app="$1"
app_l=$(echo "${app}" | tr '[:upper:]' '[:lower:]')

set -e

ELM="elm-0.19.1"

# ELM_DIR is set in every app build script before calling elm.sh
if [ -z $ELM_DIR ]; then
  echo "Missing ELM_DIR variable"
  exit 1
fi

if ! command -v ${ELM} &> /dev/null
then
  echo "# ERROR: missing ${ELM} binary"
  echo "# To install the right compiler version:"
  echo ""
  echo "$ curl -L -o ${ELM}.gz https://github.com/elm/compiler/releases/download/${ELM}/binary-for-linux-64-bit.gz"
  echo "$ gzip -d ${ELM}.gz"
  echo "$ chmod +x ${ELM}"
  echo "# then put it somewhere in your PATH"
  exit 1
fi

build_release() {
  ${ELM} make --optimize sources/${app}.elm --output=generated/rudder-${app_l}.js
  terser generated/rudder-${app_l}.js --compress 'pure_funcs="F2,F3,F4,F5,F6,F7,F8,F9,A2,A3,A4,A5,A6,A7,A8,A9",pure_getters,keep_fargs=false,unsafe_comps,unsafe' | terser --mangle --output=generated/rudder-${app_l}.min.js
    # we use the same path for dev and prod so we can't really use .min.js
  cp generated/rudder-${app_l}.min.js generated/rudder-${app_l}.js
}

build_dev() {
  ${ELM} make sources/${app}.elm --output=generated/rudder-${app_l}.js
}

# Warning: the current fs scheme does not allow multiple elm app in a plugin
cd ${ELM_DIR}/
if [ "$2" = "--release" ]; then
  (set -x; build_release)
else
  (set -x; build_dev)
fi
