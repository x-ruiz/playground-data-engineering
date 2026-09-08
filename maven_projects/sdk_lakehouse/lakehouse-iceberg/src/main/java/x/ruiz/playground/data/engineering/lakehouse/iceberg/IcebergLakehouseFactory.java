package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.spark.sql.SparkSession;
import x.ruiz.playground.data.engineering.lakehouse.core.LakehouseFactory;
import x.ruiz.playground.data.engineering.lakehouse.core.TableManager;

public class IcebergLakehouseFactory implements LakehouseFactory<IcebergCatalog> {
    @Override
    public TableManager createTableManager() {
        return new IcebergTableManager();
    }

    //TODO: After SparkSession refactor, determine which kind of spark session to return based on CloudProviderType
    @Override
    public SparkSession createSparkSession(IcebergCatalog catalog) {
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
                           .config("spark.hadoop.fs.s3a.access.key", "<AWS_ACCESS_KEY_ID>")
                           .config("spark.hadoop.fs.s3a.secret.key", "<AWS_SECRET_ACCESS_KEY>")
                           .config("spark.hadoop.fs.s3a.endpoint", "http://localhost:9000")
                           .config("spark.hadoop.fs.s3a.path.style.access", "true")
                           .getOrCreate();
    }
}
