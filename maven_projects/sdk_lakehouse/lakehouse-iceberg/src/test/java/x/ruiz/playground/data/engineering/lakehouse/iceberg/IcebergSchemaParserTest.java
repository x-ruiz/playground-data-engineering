package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcebergSchemaParserTest {

    @Test
    void shouldParsePrimitiveTypes() {
        String schemaDdl = "id BIGINT, name STRING, active BOOLEAN, rating FLOAT, balance DOUBLE";
        Schema schema = IcebergSchemaParser.parse(schemaDdl);

        assertThat(schema.columns()).hasSize(5);

        assertThat(schema.findField("id").fieldId()).isEqualTo(1);
        assertThat(schema.findField("id").type()).isEqualTo(Types.LongType.get());
        assertThat(schema.findField("id").isOptional()).isTrue();

        assertThat(schema.findField("name").fieldId()).isEqualTo(2);
        assertThat(schema.findField("name").type()).isEqualTo(Types.StringType.get());

        assertThat(schema.findField("active").fieldId()).isEqualTo(3);
        assertThat(schema.findField("active").type()).isEqualTo(Types.BooleanType.get());

        assertThat(schema.findField("rating").fieldId()).isEqualTo(4);
        assertThat(schema.findField("rating").type()).isEqualTo(Types.FloatType.get());

        assertThat(schema.findField("balance").fieldId()).isEqualTo(5);
        assertThat(schema.findField("balance").type()).isEqualTo(Types.DoubleType.get());
    }

    @Test
    void shouldParseNotNullAndDecimals() {
        String schemaDdl = "order_id STRING NOT NULL, amount DECIMAL(18, 2) NOT NULL, created_at TIMESTAMP";
        Schema schema = IcebergSchemaParser.parse(schemaDdl);

        assertThat(schema.columns()).hasSize(3);

        assertThat(schema.findField("order_id").isRequired()).isTrue();
        assertThat(schema.findField("amount").isRequired()).isTrue();
        assertThat(schema.findField("amount").type()).isEqualTo(Types.DecimalType.of(18, 2));

        assertThat(schema.findField("created_at").isOptional()).isTrue();
        assertThat(schema.findField("created_at").type()).isEqualTo(Types.TimestampType.withZone());
    }

    @Test
    void shouldHandleEmptyOrNullString() {
        assertThat(IcebergSchemaParser.parse("").columns()).isEmpty();
        assertThat(IcebergSchemaParser.parse(null).columns()).isEmpty();
    }

    @Test
    void shouldThrowOnInvalidFormat() {
        assertThatThrownBy(() -> IcebergSchemaParser.parse("invalid_column_no_type"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
