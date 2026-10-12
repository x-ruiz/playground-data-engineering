package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.spark.sql.SparkSession;
import x.ruiz.playground.data.engineering.lakehouse.core.Table;
import x.ruiz.playground.data.engineering.lakehouse.core.TableManager;
import x.ruiz.playground.data.engineering.lakehouse.core.WorkloadMode;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        String tableName = args.length > 0 ? args[0] : "taxis";
        String namespace = args.length > 1 ? args[1] : "nyc";
        String schema = args.length > 2 ? args[2] : "vendor_id BIGINT, trip_id BIGINT, fare_amount DOUBLE, ts TIMESTAMP";

        logger.info("Initializing Lakehouse SDK for table '{}.{}'", namespace, tableName);

        // 1. Configure the Iceberg REST Catalog
        IcebergCatalog icebergCatalog = IcebergCatalog.builder(CatalogType.REST)
                .name("lakehouse_iceberg")
                .uri("http://iceberg-rest:8181")
                .warehouse("s3://warehouse/")
                .s3Endpoint("http://minio:9000")
                .build();

        // 2. Initialize Factory and Table Managers
        IcebergLakehouseFactory factory = new IcebergLakehouseFactory();

        // Native pure-Java manager (does not require Spark)
        TableManager nativeManager = factory.createTableManager(icebergCatalog);

        // 3. Define the Table descriptor
        Table table = IcebergTable.builder(tableName)
                .namespace(namespace)
                .schema(schema)
                .partitionBy("vendor_id")
                .mode(WorkloadMode.BATCH)
                .property("write.format.default", "parquet")
                .build();

        // 4. Create table via Native Catalog API
        nativeManager.create(table);
        logger.info("Table exists according to native catalog: {}", nativeManager.exists(namespace, tableName));

        // 5. Optionally verify via SparkSession
        SparkSession spark = factory.createSparkSession(icebergCatalog);
        TableManager sparkManager = factory.createSparkTableManager(spark, icebergCatalog);
        logger.info("Table exists according to Spark catalog: {}", sparkManager.exists(namespace, tableName));

        spark.stop();
        logger.info("Table creation completed successfully.");
    }
}
