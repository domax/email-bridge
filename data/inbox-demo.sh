#!/usr/bin/env bash

# Standard and Error outputs of this script are available in log of
# email-bridge app.
# If script returns 0 status code then output will be at DEBUG level,
# otherwise you'll get it at ERROR level.

# Print all available environment variables
set
# Print while line to separate list of input files
echo

# Iterate thru arguments that are file names
for file; do
	# Print file info
	ls -l "$INBOX_FOLDER/$file"
done

# No error
exit 0
