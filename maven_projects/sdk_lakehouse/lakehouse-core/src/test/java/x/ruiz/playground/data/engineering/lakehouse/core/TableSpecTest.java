package x.ruiz.playground.data.engineering.lakehouse.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableSpecTest {

    @Test
    void shouldBuildTableSpecWithDefaults() {
        TableSpec spec = TableSpec.builder("users")
                .schema("id BIGINT, name STRING")
                .build();

        assertThat(spec.name()).isEqualTo("users");
        assertThat(spec.namespace()).isEqualTo("default");
        assertThat(spec.schema()).isEqualTo("id BIGINT, name STRING");
        assertThat(spec.properties()).isEmpty();
        assertThat(spec.partitionColumns()).isEmpty();
        assertThat(spec.sortColumns()).isEmpty();
        assertThat(spec.mode()).isEqualTo(WorkloadMode.BATCH);
    }

    @Test
    void shouldBuildStreamingTableSpec() {
        TableSpec spec = TableSpec.builder("events")
                .namespace("telemetry")
                .schema("device_id STRING, ts TIMESTAMP, payload STRING")
                .partitionBy("day(ts)")
                .sortBy("ts")
                .property("write.format.default", "parquet")
                .mode(WorkloadMode.STREAMING)
                .build();

        assertThat(spec.name()).isEqualTo("events");
        assertThat(spec.namespace()).isEqualTo("telemetry");
        assertThat(spec.partitionColumns()).containsExactly("day(ts)");
        assertThat(spec.sortColumns()).containsExactly("ts");
        assertThat(spec.properties()).containsEntry("write.format.default", "parquet");
        assertThat(spec.mode()).isEqualTo(WorkloadMode.STREAMING);
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        assertThatThrownBy(() -> TableSpec.builder(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Table name must not be null");
    }
}
