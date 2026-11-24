package x.ruiz.playground.data.engineering.lakehouse.core.manager;

import x.ruiz.playground.data.engineering.lakehouse.core.spec.TableSpec;

public interface TableManager {

    void createTable(TableSpec spec);
}
