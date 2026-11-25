package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnType;
import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.TableModel;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IcebergTypeMapperTest {

    @Test
    void tc011MapsAllCanonicalColumnTypesNullabilityIdsPartitionsAndIdentifiers() {
        CatalogModel catalog = new CatalogModel("data_lake");
        NamespaceModel namespace = new NamespaceModel(catalog, "market_data");
        List<ColumnModel> columns = List.of(
                new ColumnModel("c_string", ColumnType.STRING, false),
                new ColumnModel("c_int", ColumnType.INT, true),
                new ColumnModel("c_bigint", ColumnType.BIGINT, false),
                new ColumnModel("c_double", ColumnType.DOUBLE, true),
                new ColumnModel("c_boolean", ColumnType.BOOLEAN, false),
                new ColumnModel("c_date", ColumnType.DATE, false),
                new ColumnModel("c_timestamp", ColumnType.TIMESTAMP, true),
                new ColumnModel("c_decimal", ColumnType.DECIMAL, true)
        );
        TableModel partitionedTable = new TableModel(namespace, "all_types", columns, "c_date");
        TableModel unpartitionedTable = new TableModel(namespace, "unpartitioned", columns, null);

        Schema schema = IcebergTypeMapper.toIcebergSchema(columns);
        assertThat(schema.columns()).hasSize(8);

        assertField(schema.columns().get(0), 1, "c_string", Types.StringType.get(), false);
        assertField(schema.columns().get(1), 2, "c_int", Types.IntegerType.get(), true);
        assertField(schema.columns().get(2), 3, "c_bigint", Types.LongType.get(), false);
        assertField(schema.columns().get(3), 4, "c_double", Types.DoubleType.get(), true);
        assertField(schema.columns().get(4), 5, "c_boolean", Types.BooleanType.get(), false);
        assertField(schema.columns().get(5), 6, "c_date", Types.DateType.get(), false);
        assertField(schema.columns().get(6), 7, "c_timestamp", Types.TimestampType.withZone(), true);
        assertField(schema.columns().get(7), 8, "c_decimal", Types.DecimalType.of(38, 9), true);

        PartitionSpec partitionedSpec = IcebergTypeMapper.toPartitionSpec(schema, partitionedTable);
        assertThat(partitionedSpec.isPartitioned()).isTrue();
        assertThat(partitionedSpec.fields()).hasSize(1);
        PartitionField partitionField = partitionedSpec.fields().get(0);
        assertThat(partitionField.name()).isEqualTo("c_date");
        assertThat(partitionField.transform().isIdentity()).isTrue();

        PartitionSpec unpartitionedSpec = IcebergTypeMapper.toPartitionSpec(schema, unpartitionedTable);
        assertThat(unpartitionedSpec.isUnpartitioned()).isTrue();

        assertThat(IcebergTypeMapper.toIcebergNamespace(namespace)).isEqualTo(Namespace.of("market_data"));
        assertThat(IcebergTypeMapper.toIcebergTableIdentifier(partitionedTable))
                .isEqualTo(TableIdentifier.of(Namespace.of("market_data"), "all_types"));
    }

    @Test
    void tc014IcebergTypeMapperIsPackagePrivateAndFinal() {
        assertThat(Modifier.isPublic(IcebergTypeMapper.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(IcebergTypeMapper.class.getModifiers())).isTrue();
    }

    private static void assertField(
            Types.NestedField field,
            int expectedId,
            String expectedName,
            org.apache.iceberg.types.Type expectedType,
            boolean expectedOptional) {
        assertThat(field.fieldId()).isEqualTo(expectedId);
        assertThat(field.name()).isEqualTo(expectedName);
        assertThat(field.type()).isEqualTo(expectedType);
        assertThat(field.isOptional()).isEqualTo(expectedOptional);
    }
}
