# Apache Beam Application
Apache Beam Tour https://tour.beam.apache.org/tour/java/introduction/guide

mvn archetype:generate -DgroupId=com.xavierruiz.app -DartifactId=weather-pipeline -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.5 -DinteractiveMode=false

## Running the Pipeline
mvn compile exec:java -Dexec.mainClass=com.xavierruiz.app.WeatherConsumer

## Pub Sub I/O Connector
https://beam.apache.org/releases/javadoc/current/org/apache/beam/sdk/io/gcp/pubsub/PubsubIO.html

## BigQuery I/O Connector
https://beam.apache.org/documentation/io/built-in/google-bigquery/

## Dataflow Runner
gcloud dataflow flex-template run weather-pipeline-consumer \
    --project="unified-gist-464917-r7" \
    --region="us-central1" \
    --template-file-gcs-location="gs://raw_data_0spq/dataflow/weather-pipeline/templates/weather-pipeline-consumer-template.json"