#!/bin/bash
DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

## Make sure the repo is up to date
git pull
## Generate the jar file and run the Download Directory program
mvn clean package
unzip -o target/downloadDirectory-distr.zip
java -Xmx4096m -javaagent:downloadDirectory/lib/spring-instrument-4.2.4.RELEASE.jar -jar downloadDirectory/downloadDirectory.jar
