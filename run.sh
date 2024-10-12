#!/bin/bash

mvn clean install
mvn package
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -jar ./target/wikimedia-stream-filter-1.0-SNAPSHOT-jar-with-dependencies.jar