package x.ruiz.playground.data.engineering.lakehouse.core;


import x.ruiz.playground.data.engineering.lakehouse.core.session.IcebergTableManager;
import x.ruiz.playground.data.engineering.lakehouse.core.session.TableManager;
import x.ruiz.playground.data.engineering.lakehouse.models.IcebergTable;
import x.ruiz.playground.data.engineering.lakehouse.models.Table;

public class Main {
    public static void main(String[] args) {
        Table table = IcebergTable.builder(args[0])
                                  .namespace(args[1])
                                  .schema("")
                                  .build();
        TableManager icebergManager = new IcebergTableManager();
        icebergManager.create(table);
    }
}
