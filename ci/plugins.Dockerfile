ARG JDK_VERSION=11
FROM maven:3-openjdk-${JDK_VERSION}
LABEL ci=rudder/ci/plugins.Dockerfile

ARG USER_ID=1000
COPY ci/user.sh .
# For building js and python plugins
RUN ./user.sh $USER_ID ;\
    apt-get update && apt-get install -y npm python3-docopt poppler-utils
