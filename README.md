# wikimedia-stream-filter
Project consumes Kafka stream from topic and sinks to postgres database for backup, applies a wiki title url filter and 
sinks filtered messages to a new topic using [Apache Flink](https://flink.apache.org/).
## Requirements
- [Java 21](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.4%2B7)
- [Maven](https://maven.apache.org/download.cgi)
## Running Project
To run the project you execute the script **run.sh** in terminal:
```
./run.sh
```