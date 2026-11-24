package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, self-validating specification for a lakehouse table within a {@link NamespaceSpec}.
 *
 * <p>Enforces at least one column, rejects null entries and case-insensitive duplicate column names,
 * defensively copies the column list, and normalizes an optional {@code partitionColumn} to the exact
 * casing of the matching {@link ColumnSpec#name()}.
 *
 * @param namespace       non-null owning namespace specification
 * @param name            validated table identifier matching {@link LakehouseSpec#VALID_IDENTIFIER}
 * @param columns         non-empty list of unique, non-null {@link ColumnSpec} definitions
 * @param partitionColumn optional identity partition column name ({@code null} for unpartitioned tables)
 */
public record TableSpec(
        NamespaceSpec namespace,
        String name,
        List<ColumnSpec> columns,
        String partitionColumn
) implements LakehouseSpec {

    /**
     * Validates table composition, column uniqueness, and optional partition column membership,
     * defensively copying {@code columns} and normalizing {@code partitionColumn} casing.
     *
     * @throws InvalidTableSpecException if any field violates table specification invariants
     */
    public TableSpec {
        if (namespace == null) {
            throw new InvalidTableSpecException("Namespace spec must not be null");
        }
        LakehouseSpec.validateIdentifier("tableName", name, InvalidTableSpecException::new);

        if (columns == null || columns.isEmpty()) {
            throw new InvalidTableSpecException("Table must define at least one column");
        }

        Map<String, String> canonicalNames = new HashMap<>();
        for (ColumnSpec column : columns) {
            if (column == null) {
                throw new InvalidTableSpecException("Columns list must not contain null entries");
            }
            String normalizedName = column.name().toLowerCase(Locale.ROOT);
            if (canonicalNames.putIfAbsent(normalizedName, column.name()) != null) {
                throw new InvalidTableSpecException(
                        "Duplicate column name (case-insensitive): '" + column.name() + "'");
            }
        }
        columns = List.copyOf(columns);

        if (partitionColumn != null) {
            if (partitionColumn.isBlank()) {
                throw new InvalidTableSpecException(
                        "Partition column must not be blank (use null for unpartitioned tables)");
            }
            // Normalize partition column casing to match the exact defined column name for case-sensitive engines.
            String canonicalPartition = canonicalNames.get(partitionColumn.toLowerCase(Locale.ROOT));
            if (canonicalPartition == null) {
                throw new InvalidTableSpecException(
                        "Partition column '" + partitionColumn + "' does not match any defined column");
            }
            partitionColumn = canonicalPartition;
        }
    }

    /**
     * Convenience accessor returning the owning catalog via {@link NamespaceSpec#catalog()}.
     *
     * @return the owning catalog specification
     */
    public CatalogSpec catalog() {
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
