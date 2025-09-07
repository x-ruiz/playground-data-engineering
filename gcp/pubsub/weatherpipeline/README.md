# Weather Pipeline

Learning pubsub with a public weather api

## Initial Project Creation Command
mvn archetype:generate -DgroupId=com.xavierruiz.app -DartifactId=weather-producer -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.5 -DinteractiveMode=false

## Build Jars
cd producer && mvn clean package
cd consumer && mvn clean package

## Run Producer
cd producer && java -jar target/weather-producer-1.0-SNAPSHOT.jar

## Run Consumer
cd consumer && java -jar target/weather-consumer-1.0-SNAPSHOT.jar
