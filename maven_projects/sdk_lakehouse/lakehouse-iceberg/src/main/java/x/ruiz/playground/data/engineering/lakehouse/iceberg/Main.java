package x.ruiz.playground.data.engineering.lakehouse.iceberg;


import org.apache.spark.sql.SparkSession;
import x.ruiz.playground.data.engineering.lakehouse.core.LakehouseFactory;
import x.ruiz.playground.data.engineering.lakehouse.core.Table;
import x.ruiz.playground.data.engineering.lakehouse.core.TableManager;

public class Main {
    public static void main(String[] args) {
        // Specify catalog to use
        IcebergCatalog icebergCatalog = IcebergCatalog.builder(CatalogType.REST)
                .name("lakehouse_iceberg")
                .uri("http://iceberg-rest:8181")
                .build();

        // Create the IcebergLakehouseFactory and related objects
        LakehouseFactory<IcebergCatalog> icebergFactory = new IcebergLakehouseFactory();
        TableManager icebergTableManager = icebergFactory.createTableManager();
        SparkSession sparkSession = icebergFactory.createSparkSession(icebergCatalog);

        Table table = IcebergTable.builder(args[0])
                                  .namespace(args[1])
                                  .schema("")
                                  .build();

        icebergTableManager.create(sparkSession, table);
    }
}
