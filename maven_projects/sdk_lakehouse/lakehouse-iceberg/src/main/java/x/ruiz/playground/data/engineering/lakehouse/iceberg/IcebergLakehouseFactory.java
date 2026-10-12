package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.spark.sql.SparkSession;
import x.ruiz.playground.data.engineering.lakehouse.core.LakehouseFactory;
import x.ruiz.playground.data.engineering.lakehouse.core.TableManager;

public class IcebergLakehouseFactory implements LakehouseFactory<IcebergCatalog> {

    @Override
    public TableManager createTableManager() {
        IcebergCatalog defaultCatalog = IcebergCatalog.builder(CatalogType.REST)
                .name("lakehouse_iceberg")
                .uri("http://iceberg-rest:8181")
                .build();
        return new IcebergNativeTableManager(defaultCatalog);
    }

    @Override
    public TableManager createTableManager(IcebergCatalog catalog) {
        return new IcebergNativeTableManager(catalog);
    }

    public TableManager createSparkTableManager(SparkSession spark, IcebergCatalog catalog) {
        return new SparkIcebergTableManager(spark, catalog.name());
    }

    @Override
    public SparkSession createSparkSession(IcebergCatalog catalog) {
        String accessKey = catalog.s3AccessKey() != null ? catalog.s3AccessKey() : "admin";
        String secretKey = catalog.s3SecretKey() != null ? catalog.s3SecretKey() : "password";

        return SparkSession.builder()
                .appName("Iceberg Table Create")
                .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
                .config(catalog.catalogConfigPrefix(), catalog.catalogImpl())
                .config(catalog.catalogConfigPrefix() + ".type", catalog.type().name().toLowerCase())
                .config(catalog.catalogConfigPrefix() + ".uri", catalog.uri())
                .config(catalog.catalogConfigPrefix() + ".warehouse", catalog.warehouse())
                .config(catalog.catalogConfigPrefix() + ".io-impl", catalog.ioImpl())
                .config(catalog.catalogConfigPrefix() + ".s3.endpoint", catalog.s3Endpoint())
                .config(catalog.catalogConfigPrefix() + ".s3.path-style-access", catalog.s3PathStyleAccess())
                .config(catalog.catalogConfigPrefix() + ".s3.access-key-id", accessKey)
                .config(catalog.catalogConfigPrefix() + ".s3.secret-access-key", secretKey)
                .config("spark.hadoop.fs.s3a.access.key", accessKey)
                .config("spark.hadoop.fs.s3a.secret.key", secretKey)
                .config("spark.hadoop.fs.s3a.endpoint", catalog.s3Endpoint())
                .config("spark.hadoop.fs.s3a.path.style.access", catalog.s3PathStyleAccess())
                .getOrCreate();
    }
}
