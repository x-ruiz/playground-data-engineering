package x.ruiz.playground.data.engineering.lakehouse.core.model;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableModelException;

/**
 * Immutable leaf specification describing a single table column.
 *
 * <p>Intentionally does not implement {@link LakehouseModel} and holds no back-reference
 * to its owning {@link TableModel} to keep the spec graph strictly acyclic.
 *
 * @param name     validated column identifier matching {@link LakehouseModel#VALID_IDENTIFIER}
 * @param type     non-null canonical {@link ColumnType}
 * @param nullable whether the column permits {@code null} values
 */
public record ColumnModel(String name, ColumnType type, boolean nullable) {

    /**
     * Validates the column name and ensures {@code type} is non-null.
     *
     * @throws InvalidTableModelException if {@code name} is invalid or {@code type} is null
     */
    public ColumnModel {
        LakehouseModel.validateIdentifier("column name", name, InvalidTableModelException::new);
        if (type == null) {
            throw new InvalidTableModelException("Column type must not be null for column '" + name + "'");
        }
    }
}
