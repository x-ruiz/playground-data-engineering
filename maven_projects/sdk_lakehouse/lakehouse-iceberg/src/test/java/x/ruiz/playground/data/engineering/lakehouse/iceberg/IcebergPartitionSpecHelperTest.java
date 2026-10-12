package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IcebergPartitionSpecHelperTest {

    private final Schema schema = new Schema(
            Types.NestedField.required(1, "id", Types.LongType.get()),
            Types.NestedField.required(2, "category", Types.StringType.get()),
            Types.NestedField.required(3, "created_at", Types.TimestampType.withZone())
    );

    @Test
    void shouldBuildUnpartitionedSpecWhenEmpty() {
        PartitionSpec spec = IcebergPartitionSpecHelper.buildPartitionSpec(schema, List.of());
        assertThat(spec.isUnpartitioned()).isTrue();
    }

    @Test
    void shouldBuildIdentityPartitionSpec() {
        PartitionSpec spec = IcebergPartitionSpecHelper.buildPartitionSpec(schema, List.of("category"));
        assertThat(spec.isPartitioned()).isTrue();
        assertThat(spec.fields()).hasSize(1);
        assertThat(spec.fields().get(0).name()).isEqualTo("category");
    }

    @Test
    void shouldBuildTransformPartitionSpec() {
        PartitionSpec spec = IcebergPartitionSpecHelper.buildPartitionSpec(schema, List.of("day(created_at)", "bucket(16, id)"));
        assertThat(spec.isPartitioned()).isTrue();
        assertThat(spec.fields()).hasSize(2);
        assertThat(spec.fields().get(0).transform().toString()).isEqualTo("day");
        assertThat(spec.fields().get(1).transform().toString()).isEqualTo("bucket[16]");
    }
}
