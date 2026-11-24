package x.ruiz.playground.data.engineering.lakehouse.core;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record TableSpec(
        NamespaceSpec namespace,
        String name,
        List<ColumnSpec> columns,
        String partitionColumn
) implements LakehouseSpec {

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
            String canonicalPartition = canonicalNames.get(partitionColumn.toLowerCase(Locale.ROOT));
            if (canonicalPartition == null) {
                throw new InvalidTableSpecException(
                        "Partition column '" + partitionColumn + "' does not match any defined column");
            }
            partitionColumn = canonicalPartition;
        }
    }

    public CatalogSpec catalog() {
        return namespace.catalog();
    }

    @Override
    public String fullyQualifiedName() {
        return namespace.fullyQualifiedName() + "." + name;
    }

    public boolean isPartitioned() {
        return partitionColumn != null;
    }

    public Optional<String> partitionColumnOptional() {
        return Optional.ofNullable(partitionColumn);
    }
}
