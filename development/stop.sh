#!/usr/bin/env bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# shutdown all docker container
docker compose -f "$SCRIPT_DIR/docker-compose.yml" down -v

cd $SCRIPT_DIR/..
./gradlew -stop