FROM rust
LABEL ci=rudder/ci/typos.Dockerfile
ARG VERSION

RUN cargo install -f typos-cli --version =$VERSION
