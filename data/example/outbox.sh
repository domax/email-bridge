#!/bin/bash

# Go to the script's folder
cd "`dirname "$0"`"

# This script is invoked manually and doesn't content predefined env variables as input.sh does.
# So, let's define some needed env variables.
EMAIL_TAG_OUTGOING=git-ews-eb
OUTBOX_SCRIPT="$(find `pwd` -name "`basename "$0"`")"
OUTBOX_FOLDER="`dirname "$OUTBOX_SCRIPT"`/outbox"

# Go to the project's Git folder
cd "`dirname "$OUTBOX_SCRIPT"`/../.."

# Define Git reference(s) that should be included into bundle
REF=master
if [ -n "`git tag | grep $EMAIL_TAG_OUTGOING`" ]; then
	REF=$EMAIL_TAG_OUTGOING..$REF
fi
# Create bundle and put it into outbox folder
git bundle create "$OUTBOX_FOLDER/eb-`git rev-parse HEAD`.bundle" $REF

# Apply/Move a specific Git tag to current commit
git tag -f $EMAIL_TAG_OUTGOING
