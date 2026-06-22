# EXAMPLE CODE NOT WRITTEN BY ME. USED AS REFERENCE FOR LEARNING

mvn compile exec:java -Dexec.mainClass=org.apache.beam.examples.WordCount \
    -Dexec.args="--inputFile=inputs/kinglear.txt --output=counts" -Pdirect-runner