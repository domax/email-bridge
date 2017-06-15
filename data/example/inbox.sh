#!/bin/bash

# Go to the project's Git folder
cd ../../

# Loop through input files that are git bundles
for file; do
	# Apply a bundle file into local Git repo
	git fetch "data/example/$INBOX_FOLDER/$file" master
	git merge FETCH_HEAD
	# If merge was unsuccessful then exit with error code 100
	[ ! $? ] && exit 100
	# If merge was successful then remove bundle
	rm "data/example/$INBOX_FOLDER/$file"
done

# Apply/Move a specific Git tag to current commit
git tag -f $EMAIL_TAG_INCOMING

# Push update if origin is configured
if [ -n "`git config --get remote.origin.url`" ]; then
	git push
fi
