SPARK_MASTER ?= spark://spark-iceberg:7077

.PHONY: deploy spark-sql

deploy:
	mvn clean package -DskipTests
	docker cp lakehouse-core/target/lakehouse-core-1.0-SNAPSHOT.jar spark-iceberg:/tmp/app.jar
	docker exec -it spark-iceberg spark-submit \
		--master $(SPARK_MASTER) \
		--class x.ruiz.playground.data.engineering.lakehouse.core.Main \
		/tmp/app.jar \
		"test_table" "test_namespace"

spark-sql:
	docker exec -it spark-iceberg spark-sql \
		--master $(SPARK_MASTER)

