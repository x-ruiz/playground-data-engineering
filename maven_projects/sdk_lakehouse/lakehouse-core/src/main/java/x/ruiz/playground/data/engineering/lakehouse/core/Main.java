package x.ruiz.playground.data.engineering.lakehouse.core;

import x.ruiz.playground.data.engineering.lakehouse.models.catalog.IcebergCatalogType;
import x.ruiz.playground.data.engineering.lakehouse.models.catalog.OSSTable;

public class Main {
    public static void main(String[] args) {
        System.out.println("Running Iceberg Table Create");

        IcebergCatalogType type = IcebergCatalogType.REST;
        String namespace = "default";
        String name = "test_table";
        String schema = "id bigint, name string";
        OSSTable table = new IcebergTable();
        table.create(namespace, name, schema);
    }
}
