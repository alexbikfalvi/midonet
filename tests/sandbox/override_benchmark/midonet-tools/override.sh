#!/bin/bash

# Install the latest pagackes from the local repository
LOCAL_REPO_FILE=/etc/apt/sources.list.d/midonet-local.list
echo "deb file:/packages /" > $LOCAL_REPO_FILE
apt-get update -o Dir::Etc::sourcelist=$LOCAL_REPO_FILE

# Failfast if we cannot update the packages locally
apt-get install -qy --force-yes midonet-tools/local || exit 1

# Run tools
exec /run-midonettools.sh
