package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;

/**
 * Immutable leaf specification describing a single table column.
 *
 * <p>Intentionally does not implement {@link LakehouseSpec} and holds no back-reference
 * to its owning {@link TableSpec} to keep the spec graph strictly acyclic.
 *
 * @param name     validated column identifier matching {@link LakehouseSpec#VALID_IDENTIFIER}
 * @param type     non-null canonical {@link ColumnType}
 * @param nullable whether the column permits {@code null} values
 */
public record ColumnSpec(String name, ColumnType type, boolean nullable) {

    /**
     * Validates the column name and ensures {@code type} is non-null.
     *
     * @throws InvalidTableSpecException if {@code name} is invalid or {@code type} is null
     */
    public ColumnSpec {
        LakehouseSpec.validateIdentifier("column name", name, InvalidTableSpecException::new);
        if (type == null) {
            throw new InvalidTableSpecException("Column type must not be null for column '" + name + "'");
        }
    }
}
