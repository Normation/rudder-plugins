#!/bin/sh

# 
# This file help find M2_REPO. 
# This is not trivial as M2_REPO can be defined in a multitude of
# place, so the safe solution is to ask mvn directly. 
# But even that is not easy, because mvn doesn't reply simply. 
# And as it is very long, we want to cache the result
#

SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
M2_REPO_LOC_CACHE=${SCRIPT_DIR}/m2_location.cache

case $1 in

  "clean")
    rm -f ${M2_REPO_LOC_CACHE}
   ;;

  *)
    if [ ! -f ${M2_REPO_LOC_CACHE} ]; then
      mvn ${MVN_PARAMS} help:evaluate -Dexpression="localRepository" | grep basedir | sed -e "s/.*>\(.*\)<.*/\1/" > ${M2_REPO_LOC_CACHE}
    fi
    cat ${M2_REPO_LOC_CACHE}

esac

