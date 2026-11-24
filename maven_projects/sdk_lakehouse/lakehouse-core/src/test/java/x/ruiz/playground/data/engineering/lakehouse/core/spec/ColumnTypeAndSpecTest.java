package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColumnTypeAndSpecTest {

    @ParameterizedTest
    @CsvSource({
            "'STRING', STRING",
            "'string', STRING",
            "'  int  ', INT",
            "'bigint', BIGINT",
            "'Double', DOUBLE",
            "'boolean', BOOLEAN",
            "' date ', DATE",
            "'Timestamp', TIMESTAMP",
            "'DECIMAL', DECIMAL"
    })
    void fromStringParsesCaseInsensitivelyAndTrimsWhitespace(String raw, ColumnType expected) {
        assertThat(ColumnType.fromString(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "VARCHR", "DECIMAL(10,2)", "BIGINTT"})
    void fromStringRejectsNullBlankOrUnknownTypes(String raw) {
        var assertion = assertThatThrownBy(() -> ColumnType.fromString(raw))
                .isInstanceOf(InvalidTableSpecException.class);
        if (raw != null && !raw.isBlank()) {
            assertion.hasMessageContaining(raw);
        }
    }

    @Test
    void columnSpecIsStandaloneLeafRecord() {
        ColumnSpec column = new ColumnSpec("amount", ColumnType.DECIMAL, true);

        assertThat(column).isNotInstanceOf(LakehouseSpec.class);
        assertThat(column.name()).isEqualTo("amount");
        assertThat(column.type()).isEqualTo(ColumnType.DECIMAL);
        assertThat(column.nullable()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "col.name", "col name", "1col", "col-name"})
    void columnSpecRejectsInvalidName(String badName) {
        assertThatThrownBy(() -> new ColumnSpec(badName, ColumnType.STRING, false))
                .isInstanceOf(InvalidTableSpecException.class);
    }

    @Test
    void columnSpecRejectsNullType() {
        assertThatThrownBy(() -> new ColumnSpec("id", null, false))
                .isInstanceOf(InvalidTableSpecException.class)
                .hasMessageContaining("id");
    }
}
