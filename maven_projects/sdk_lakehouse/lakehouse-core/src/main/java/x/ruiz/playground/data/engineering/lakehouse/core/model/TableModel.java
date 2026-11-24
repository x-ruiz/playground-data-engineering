package x.ruiz.playground.data.engineering.lakehouse.core.model;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableModelException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, self-validating specification for a lakehouse table within a {@link NamespaceModel}.
 *
 * <p>Enforces at least one column, rejects null entries and case-insensitive duplicate column names,
 * defensively copies the column list, and normalizes an optional {@code partitionColumn} to the exact
 * casing of the matching {@link ColumnModel#name()}.
 *
 * @param namespace       non-null owning namespace specification
 * @param name            validated table identifier matching {@link LakehouseModel#VALID_IDENTIFIER}
 * @param columns         non-empty list of unique, non-null {@link ColumnModel} definitions
 * @param partitionColumn optional identity partition column name ({@code null} for unpartitioned tables)
 */
public record TableModel(
        NamespaceModel namespace,
        String name,
        List<ColumnModel> columns,
        String partitionColumn
) implements LakehouseModel {

    /**
     * Validates table composition, column uniqueness, and optional partition column membership,
     * defensively copying {@code columns} and normalizing {@code partitionColumn} casing.
     *
     * @throws InvalidTableModelException if any field violates table specification invariants
     */
    public TableModel {
        if (namespace == null) {
            throw new InvalidTableModelException("Namespace model must not be null");
        }
        LakehouseModel.validateIdentifier("tableName", name, InvalidTableModelException::new);

        if (columns == null || columns.isEmpty()) {
            throw new InvalidTableModelException("Table must define at least one column");
        }

        Map<String, String> canonicalNames = new HashMap<>();
        for (ColumnModel column : columns) {
            if (column == null) {
                throw new InvalidTableModelException("Columns list must not contain null entries");
            }
            String normalizedName = column.name().toLowerCase(Locale.ROOT);
            if (canonicalNames.putIfAbsent(normalizedName, column.name()) != null) {
                throw new InvalidTableModelException(
                        "Duplicate column name (case-insensitive): '" + column.name() + "'");
            }
        }
        columns = List.copyOf(columns);

        if (partitionColumn != null) {
            if (partitionColumn.isBlank()) {
                throw new InvalidTableModelException(
                        "Partition column must not be blank (use null for unpartitioned tables)");
            }
            // Normalize partition column casing to match the exact defined column name for case-sensitive engines.
            String canonicalPartition = canonicalNames.get(partitionColumn.toLowerCase(Locale.ROOT));
            if (canonicalPartition == null) {
                throw new InvalidTableModelException(
                        "Partition column '" + partitionColumn + "' does not match any defined column");
            }
            partitionColumn = canonicalPartition;
        }
    }

    /**
     * Convenience accessor returning the owning catalog via {@link NamespaceModel#catalog()}.
     *
     * @return the owning catalog specification
     */
    public CatalogModel catalog() {
        return namespace.catalog();
    }

    /**
     * Returns the fully qualified table identifier formatted as {@code <catalog>.<namespace>.<table>}.
     *
     * @return dot-delimited catalog, namespace, and table name
     */
    @Override
    public String fullyQualifiedName() {
        return namespace.fullyQualifiedName() + "." + name;
    }

    /**
     * Indicates whether this table defines a partition column.
     *
     * @return {@code true} if {@link #partitionColumn()} is non-null
     */
    public boolean isPartitioned() {
        return partitionColumn != null;
    }

    /**
     * Returns the optional identity partition column name.
     *
     * @return an {@link Optional} containing the canonical partition column name, or empty if unpartitioned
     */
    public Optional<String> partitionColumnOptional() {
        return Optional.ofNullable(partitionColumn);
    }
}
