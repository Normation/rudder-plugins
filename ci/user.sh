#!/bin/sh

set -e

# Jenkins user with correct uid
USER_ID=$1
mkdir /home/jenkins
if id ubuntu >/dev/null 2>&1; then
    userdel -r ubuntu # user UID 1000 already exist on Temurin, it's `ubuntu`
fi
useradd -r -u $USER_ID -d /home/jenkins jenkins
chown jenkins /home/jenkins
