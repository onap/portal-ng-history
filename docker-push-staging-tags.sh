#!/bin/bash
# Copyright (c) 2026. Deutsche Telekom AG
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

echo "---> docker-push-staging-tags.sh"

# Tags and pushes the already-built Docker image with versioned snapshot tags,
# aligning with the ONAP convention used by Maven-based projects (fabric8 plugin).
#
# Expected environment variables (set by Jenkins or GitHub Actions):
#   CONTAINER_PUSH_REGISTRY - Docker registry to push to (e.g. nexus3.onap.org:10003)
#   DOCKER_NAME             - Image name without registry (e.g. onap/portal-ng/ui)
#
# Reads version from version.properties in the script's directory.
# Produces these additional tags (beyond the existing 'latest'):
#   {major}.{minor}.{patch}                          e.g. 0.2.0
#   {major}.{minor}.{patch}-STAGING-latest           e.g. 0.2.0-STAGING-latest
#   {major}.{minor}.{patch}-{timestamp}              e.g. 0.2.0-20260507T1430

set -eu -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Read version components from version.properties
source_version() {
    local props_file="$SCRIPT_DIR/version.properties"
    if [[ ! -f "$props_file" ]]; then
        echo "ERROR: version.properties not found at $props_file"
        exit 1
    fi
    # Source only the simple variable assignments (major, minor, patch)
    # Strip carriage returns to handle Windows-style line endings
    eval "$(grep -E '^(major|minor|patch)=' "$props_file" | tr -d '\r')"
}

source_version

VERSION="${major}.${minor}.${patch}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M)"
SOURCE_IMAGE="$CONTAINER_PUSH_REGISTRY/$DOCKER_NAME:latest"

TAGS=(
    "$VERSION"
    "$VERSION-STAGING-latest"
    "$VERSION-$TIMESTAMP"
)

echo "Source image: $SOURCE_IMAGE"
echo "Version: $VERSION"
echo "Timestamp: $TIMESTAMP"

for tag in "${TAGS[@]}"; do
    TARGET="$CONTAINER_PUSH_REGISTRY/$DOCKER_NAME:$tag"
    echo "Tagging: $TARGET"
    docker tag "$SOURCE_IMAGE" "$TARGET"
    echo "Pushing: $TARGET"
    docker push "$TARGET"
done

echo "---> docker-push-staging-tags.sh ends"
