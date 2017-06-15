#!/bin/bash

# Go to the script's folder
cd "`dirname "$0"`"

# Define needed variables
if [ -z "$APP_NAME" ]; then
	APP_NAME="Email bridge"
fi
if [ -z "$JAR_FILE" ]; then
	JAR_FILE=`ls -1 ../../target/email-bridge-*-standalone.jar | head -1`
fi
if [ -z "$CFG_FILE" ]; then
	CFG_FILE=config.properties
fi
if [ -z "$LOG_FILE" ]; then
	LOG_FILE=app.log
fi
if [ -z "$PID_FILE" ]; then
	PID_FILE=app.pid
fi
PID="`cat $PID_FILE 2>/dev/null`"
if [ -n "$PID" -a "$OSTYPE" = "cygwin" ]; then
	PID="`ps | awk "{if (\$4 == $PID) print \$1}"`"
fi

function start() {
	echo -n "Starting $APP_NAME: "
	if [ -n "$PID" ]; then
		echo "failed - process is running with PID $PID";
		exit 2
	fi
	# Make sure both working folders are existent
	mkdir inbox outbox >/dev/null 2>&1
	# Run and daemonize application
	nohup java -jar "$JAR_FILE" -f "$CFG_FILE" >"$LOG_FILE" 2>&1 &
	echo "success"
}

function stop() {
	echo -n "Stopping $APP_NAME: "
	if [ -z "$PID" ]; then
		echo "failed - process isn't running";
		exit 3
	fi
	kill $PID
	echo "success"
}

case "$1" in
	start) start;;
	stop) stop;;
	restart)
		if [ -n "$PID" ]; then
			stop
			sleep 2
			PID=""
		fi
		start
		;;
	status)
		if [ -z "$PID" ]; then
			echo "$APP_NAME isn't running";
			exit 3
		fi
		echo "$APP_NAME is running with PID $PID";
		;;
	*)
		echo "Usage: $0 {start|stop|restart|status}"
		exit 1
		;;
esac

exit 0