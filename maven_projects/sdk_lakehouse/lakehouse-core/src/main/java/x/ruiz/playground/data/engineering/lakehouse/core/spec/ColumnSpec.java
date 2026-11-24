package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;

public record ColumnSpec(String name, ColumnType type, boolean nullable) {

    public ColumnSpec {
        LakehouseSpec.validateIdentifier("column name", name, InvalidTableSpecException::new);
        if (type == null) {
            throw new InvalidTableSpecException("Column type must not be null for column '" + name + "'");
        }
    }
}
