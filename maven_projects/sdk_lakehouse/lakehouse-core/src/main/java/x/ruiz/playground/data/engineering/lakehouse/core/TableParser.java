package x.ruiz.playground.data.engineering.lakehouse.core;

import org.apache.spark.sql.catalyst.plans.logical.TableSpec;

public interface TableParser {
    public TableSpec parseData();
}
