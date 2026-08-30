.PHONY: deploy

deploy:
	mvn clean package -DskipTests
	docker cp target/sdk.oss.tables-1.0-SNAPSHOT.jar spark-iceberg:/tmp/app.jar
	docker exec -it spark-iceberg spark-submit \
		--master spark://dc10631fcc95:7077 \
		--class x.ruiz.playground.data.engineering.sdk.oss.tables.Main \
		/tmp/app.jar

spark-sql:
	docker exec -it spark-iceberg spark-sql \
		--master spark://dc10631fcc95:7077
