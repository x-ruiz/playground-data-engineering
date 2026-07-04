package x.ruiz.playground.data.engineering.sdk.oss.tables;

import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.Catalog;

public interface OSSTable {
    void create(String namespace, String name, String schema);

    void drop();

//    void rename();
}
