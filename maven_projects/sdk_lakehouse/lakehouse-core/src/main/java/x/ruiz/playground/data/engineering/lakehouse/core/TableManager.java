package x.ruiz.playground.data.engineering.lakehouse.core;

import org.apache.spark.sql.SparkSession;

public interface TableManager {
    void create(SparkSession spark, Table table);

    void drop();
}
