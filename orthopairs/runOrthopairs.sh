#!/bin/bash
DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

## Update repo
git pull
## Create new jar file with orthoinference code
mvn clean compile assembly:single

## Run Orthopairs file generating script
echo "java -jar target/orthopairs-0.0.1-SNAPSHOT-jar-with-dependencies.jar" 
java -jar target/orthopairs-0.0.1-SNAPSHOT-jar-with-dependencies.jar
echo "Finished Orthopairs"
