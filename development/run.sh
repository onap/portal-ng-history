#!/usr/bin/env bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

cd $SCRIPT_DIR/..
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun