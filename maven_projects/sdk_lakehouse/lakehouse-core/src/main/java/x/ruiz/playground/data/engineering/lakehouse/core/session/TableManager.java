package x.ruiz.playground.data.engineering.lakehouse.core.session;

import x.ruiz.playground.data.engineering.lakehouse.models.Table;

public interface TableManager {
    void create(Table table);

    void drop();
}
