#!/bin/sh

set -e

# Takes a --release parameter for optimization
# Takes a watch parameter to enable watch mode

RELEASE=false
WATCH=false
if [ "$1" = "--release" ]; then
  RELEASE=true
fi
if [ "$1" = "--watch" ]; then
  WATCH=true
fi

# Work locally
cd "$(dirname "$0")"

if [ "$RELEASE" = true ]; then
  # Ensure clean state for release
  rm -rf node_modules
fi

# We need the gulpfile in current working directory, and to remove the old one (.js) if it is present
if [ -f "gulpfile.js" ]; then
rm gulpfile.js
fi
cp ../../../plugins-common/gulpfile.mjs .

# Ensure correct versions
npm_config_loglevel=error npm ci --no-audit

if [ "$RELEASE" = true ]; then
  npx gulp --production
elif [ "$WATCH" = true ]; then
  npx gulp watch
else
  npx gulp
fi
