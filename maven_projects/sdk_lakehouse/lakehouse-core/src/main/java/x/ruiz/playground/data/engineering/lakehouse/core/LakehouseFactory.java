package x.ruiz.playground.data.engineering.lakehouse.core;

import org.apache.spark.sql.SparkSession;

public interface LakehouseFactory<C extends Catalog> {
    TableManager createTableManager();
    SparkSession createSparkSession(C catalog);
}
