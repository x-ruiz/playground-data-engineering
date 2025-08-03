Apache Beam Tour https://tour.beam.apache.org/tour/java/introduction/guide

mvn archetype:generate -DgroupId=com.xavierruiz.app -DartifactId=weather-pipeline -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.5 -DinteractiveMode=false

## Running the Pipeline
mvn compile exec:java -Dexec.mainClass=com.xavierruiz.app.WeatherConsumer -Dexec.args=--inputFile=src/input.txt