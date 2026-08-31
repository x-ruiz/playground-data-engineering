package x.ruiz.playground.data.engineering.lakehouse.models.catalog;

import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.Catalog;
import x.ruiz.playground.data.engineering.lakehouse.models.internal.DefaultIcebergTable;

public interface IcebergTable {
    void create(String namespace, String name, String schema);

    void drop();

    static IcebergTable createDefault(String tableName) {
        return new DefaultIcebergTable();
    }
}
