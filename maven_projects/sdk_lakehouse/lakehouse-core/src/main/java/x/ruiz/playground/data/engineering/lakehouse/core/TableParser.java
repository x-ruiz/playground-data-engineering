package x.ruiz.playground.data.engineering.lakehouse.core;

import x.ruiz.playground.data.engineering.lakehouse.core.TableSpec;

public interface TableParser {
    public TableSpec parseData();
}
