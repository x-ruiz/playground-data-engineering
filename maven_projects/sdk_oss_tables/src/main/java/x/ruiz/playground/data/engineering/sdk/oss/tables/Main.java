package x.ruiz.playground.data.engineering.sdk.oss.tables;

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
