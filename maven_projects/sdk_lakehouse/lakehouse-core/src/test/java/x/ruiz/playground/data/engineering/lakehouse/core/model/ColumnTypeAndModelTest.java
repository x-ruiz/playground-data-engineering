package x.ruiz.playground.data.engineering.lakehouse.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableModelException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColumnTypeAndModelTest {

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
                .isInstanceOf(InvalidTableModelException.class);
        if (raw != null && !raw.isBlank()) {
            assertion.hasMessageContaining(raw);
        }
    }

    @Test
    void columnSpecIsStandaloneLeafRecord() {
        ColumnModel column = new ColumnModel("amount", ColumnType.DECIMAL, true);

        assertThat(column).isNotInstanceOf(LakehouseModel.class);
        assertThat(column.name()).isEqualTo("amount");
        assertThat(column.type()).isEqualTo(ColumnType.DECIMAL);
        assertThat(column.nullable()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "col.name", "col name", "1col", "col-name"})
    void columnSpecRejectsInvalidName(String badName) {
        assertThatThrownBy(() -> new ColumnModel(badName, ColumnType.STRING, false))
                .isInstanceOf(InvalidTableModelException.class);
    }

    @Test
    void columnSpecRejectsNullType() {
        assertThatThrownBy(() -> new ColumnModel("id", null, false))
                .isInstanceOf(InvalidTableModelException.class)
                .hasMessageContaining("id");
    }
}
