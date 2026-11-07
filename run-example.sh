#!/usr/bin/env sh
set -eu
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java')
java -cp "$BUILD_DIR" dev.infrai.fieldservice.FieldServiceBatchExample "$@"
