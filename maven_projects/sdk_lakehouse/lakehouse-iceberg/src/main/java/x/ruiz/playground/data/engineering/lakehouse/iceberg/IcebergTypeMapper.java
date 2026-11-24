package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnType;
import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.TableModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Package-private pure translation helpers converting {@code lakehouse-core} models into
 * Apache Iceberg schemas, partition specs, namespaces, and table identifiers.
 */
final class IcebergTypeMapper {

    /** Default decimal precision used for unparameterized {@link ColumnType#DECIMAL} (BigQuery NUMERIC-compatible). */
    static final int DEFAULT_DECIMAL_PRECISION = 38;

    /** Default decimal scale used for unparameterized {@link ColumnType#DECIMAL} (BigQuery NUMERIC-compatible). */
    static final int DEFAULT_DECIMAL_SCALE = 9;

    private IcebergTypeMapper() {
    }

    /**
     * Maps a canonical {@link ColumnType} to its Apache Iceberg {@link Type}.
     *
     * @param columnType non-null canonical column type
     * @return corresponding Iceberg primitive type
     */
    static Type toIcebergType(ColumnType columnType) {
        return switch (columnType) {
            case STRING -> Types.StringType.get();
            case INT -> Types.IntegerType.get();
            case BIGINT -> Types.LongType.get();
            case DOUBLE -> Types.DoubleType.get();
            case BOOLEAN -> Types.BooleanType.get();
            case DATE -> Types.DateType.get();
            case TIMESTAMP -> Types.TimestampType.withZone();
            case DECIMAL -> Types.DecimalType.of(DEFAULT_DECIMAL_PRECISION, DEFAULT_DECIMAL_SCALE);
        };
    }

    /**
     * Translates an ordered list of {@link ColumnModel} definitions into an Iceberg {@link Schema}
     * with deterministic 1-based field IDs.
     *
     * @param columns ordered table column definitions
     * @return Iceberg {@link Schema}
     */
    static Schema toIcebergSchema(List<ColumnModel> columns) {
        List<Types.NestedField> fields = new ArrayList<>(columns.size());
        int fieldId = 1;
        for (ColumnModel column : columns) {
            Type icebergType = toIcebergType(column.type());
            Types.NestedField field = column.nullable()
                    ? Types.NestedField.optional(fieldId, column.name(), icebergType)
                    : Types.NestedField.required(fieldId, column.name(), icebergType);
            fields.add(field);
            fieldId++;
        }
        return new Schema(fields);
    }

    /**
     * Builds an Iceberg {@link PartitionSpec} for {@code tableModel} against {@code schema}.
     *
     * @param schema     Iceberg schema built from {@code tableModel.columns()}
     * @param tableModel validated table model
     * @return identity partition spec if partitioned, or {@link PartitionSpec#unpartitioned()}
     */
    static PartitionSpec toPartitionSpec(Schema schema, TableModel tableModel) {
        if (!tableModel.isPartitioned()) {
            return PartitionSpec.unpartitioned();
        }
        return PartitionSpec.builderFor(schema)
                .identity(tableModel.partitionColumn())
                .build();
    }

    /**
     * Converts a {@link NamespaceModel} to an Iceberg {@link Namespace}.
     *
     * @param namespaceModel validated namespace model
     * @return single-level Iceberg {@link Namespace}
     */
    static Namespace toIcebergNamespace(NamespaceModel namespaceModel) {
        return Namespace.of(namespaceModel.name());
    }

    /**
     * Converts a {@link TableModel} to an Iceberg {@link TableIdentifier}.
     *
     * @param tableModel validated table model
     * @return Iceberg {@link TableIdentifier}
     */
    static TableIdentifier toIcebergTableIdentifier(TableModel tableModel) {
        return TableIdentifier.of(toIcebergNamespace(tableModel.namespace()), tableModel.name());
    }
}
