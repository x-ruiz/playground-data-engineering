package x.ruiz.playground.data.engineering.sdk.oss.tables;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.spark.sql.SparkSession;



public class IcebergTable implements OSSTable {
    private static final Logger logger = LogManager.getLogger();
    private final SparkSession spark;

    public IcebergTable() {
        this.spark = SparkSession.builder()
                .appName("Iceberg Table Create")
                .master("local[*]")
                .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
                .config("spark.sql.catalog.demo", "org.apache.iceberg.spark.SparkCatalog")
                .config("spark.sql.catalog.demo.type", "rest")
                .config("spark.sql.catalog.demo.uri", "http://localhost:8181")
                .config("spark.sql.catalog.demo.warehouse", "s3://warehouse/")
                .config("spark.sql.catalog.demo.io-impl", "org.apache.iceberg.aws.s3.S3FileIO")
                .config("spark.sql.catalog.demo.s3.endpoint", "http://localhost:9000")
                .config("spark.sql.catalog.demo.s3.path-style-access", "true")
                .config("spark.hadoop.fs.s3a.access.key", "<AWS_ACCESS_KEY_ID>")
                .config("spark.hadoop.fs.s3a.secret.key", "<AWS_SECRET_ACCESS_KEY>")
                .config("spark.hadoop.fs.s3a.endpoint", "http://localhost:9000")
                .config("spark.hadoop.fs.s3a.path.style.access", "true")
                .getOrCreate();
    }

    @Override
    @SuppressWarnings("SqlSourceToSinkFlow")
    public void create(String namespace, String name, String schema) {
        logger.info("Creating Table with namespace: {}, name: {}, schema: {}", namespace, name, schema);
        spark.sql(String.format("CREATE DATABASE IF NOT EXISTS demo.%s", namespace));
        spark.sql(String.format("""
                CREATE TABLE IF NOT EXISTS demo.%s.%s (
                    id BIGINT,
                    name STRING,
                    ts TIMESTAMP
                )
                USING iceberg
                """, namespace, name));

        logger.info("Table Created");
    }

    @Override
    public void drop() {

    }
}