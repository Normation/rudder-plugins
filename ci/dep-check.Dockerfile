ARG JDK_VERSION=11
FROM maven:3-openjdk-${JDK_VERSION}

ARG USER_ID=1000
COPY ci/user.sh .
RUN ./user.sh $USER_ID
