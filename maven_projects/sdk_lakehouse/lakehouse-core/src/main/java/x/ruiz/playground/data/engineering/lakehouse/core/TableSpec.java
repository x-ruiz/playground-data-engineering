package x.ruiz.playground.data.engineering.lakehouse.core;

public record TableSpec(
        String catalog,
        String namespace,
        String tableName,
        Column[] columns,
        String partitionColumn
) {}