#!/bin/bash
VERSION=`head -n1 project.clj | sed 's/^.*\([[:digit:]]\.[[:digit:]]\.[[:digit:]]\).*/\1/'`
echo "version($VERSION)"
docker build --build-arg DOCKER_TAG=$VERSION -t toyadaptoy/knothink:$VERSION -t toyadaptor/knothink:latest .
