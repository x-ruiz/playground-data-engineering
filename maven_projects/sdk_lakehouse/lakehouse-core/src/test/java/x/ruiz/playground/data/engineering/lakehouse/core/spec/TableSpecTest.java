package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableSpecTest {

    private static final CatalogSpec DATA_LAKE = new CatalogSpec("data_lake");
    private static final NamespaceSpec MARKET_DATA = new NamespaceSpec(DATA_LAKE, "market_data");

    private static final List<ColumnSpec> SAMPLE_COLUMNS = List.of(
            new ColumnSpec("date", ColumnType.DATE, false),
            new ColumnSpec("id", ColumnType.BIGINT, false),
            new ColumnSpec("amount", ColumnType.DECIMAL, true)
    );

    @Test
    void composesNamespaceSpecAndExposesCatalogAndFqn() {
        TableSpec spec = new TableSpec(MARKET_DATA, "transactions", SAMPLE_COLUMNS, "date");

        assertThat(spec).isInstanceOf(LakehouseSpec.class);
        assertThat(spec.catalog()).isEqualTo(DATA_LAKE);
        assertThat(spec.namespace()).isEqualTo(MARKET_DATA);
        assertThat(spec.name()).isEqualTo("transactions");
        assertThat(spec.fullyQualifiedName()).isEqualTo("data_lake.market_data.transactions");
        assertThat(spec.columns()).containsExactlyElementsOf(SAMPLE_COLUMNS);
        assertThat(spec.isPartitioned()).isTrue();
        assertThat(spec.partitionColumnOptional()).contains("date");
    }

    @Test
    void partitionColumnMatchesExistingColumnCaseInsensitivelyAndNormalizesToCanonicalName() {
        TableSpec spec = new TableSpec(MARKET_DATA, "transactions", SAMPLE_COLUMNS, "DATE");

        assertThat(spec.isPartitioned()).isTrue();
        assertThat(spec.partitionColumn()).isEqualTo("date");
        assertThat(spec.partitionColumnOptional()).contains("date");
    }

    @Test
    void unpartitionedTableSpecAllowsNullPartitionColumn() {
        TableSpec spec = new TableSpec(MARKET_DATA, "transactions", SAMPLE_COLUMNS, null);

        assertThat(spec.isPartitioned()).isFalse();
        assertThat(spec.partitionColumnOptional()).isEmpty();
    }

    @Test
    void rejectsNullNamespaceSpec() {
        assertThatThrownBy(() -> new TableSpec(null, "transactions", SAMPLE_COLUMNS, "date"))
                .isInstanceOf(InvalidTableSpecException.class)
                .isInstanceOf(LakehouseException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "bad.table", "table name", "9table", "bad-table", "name$"})
    void rejectsInvalidTableName(String badName) {
        assertThatThrownBy(() -> new TableSpec(MARKET_DATA, badName, SAMPLE_COLUMNS, null))
                .isInstanceOf(InvalidTableSpecException.class)
                .isInstanceOf(LakehouseException.class);
    }

    @Test
    void rejectsNullOrEmptyColumns() {
        assertThatThrownBy(() -> new TableSpec(MARKET_DATA, "transactions", null, null))
                .isInstanceOf(InvalidTableSpecException.class);

        assertThatThrownBy(() -> new TableSpec(MARKET_DATA, "transactions", List.of(), null))
                .isInstanceOf(InvalidTableSpecException.class);
    }

    @Test
    void rejectsNullColumnEntry() {
        List<ColumnSpec> withNull = new ArrayList<>();
        withNull.add(new ColumnSpec("id", ColumnType.BIGINT, false));
        withNull.add(null);

        assertThatThrownBy(() -> new TableSpec(MARKET_DATA, "transactions", withNull, null))
                .isInstanceOf(InvalidTableSpecException.class);
    }

    @Test
    void rejectsDuplicateColumnNamesCaseInsensitively() {
        List<ColumnSpec> duplicates = List.of(
                new ColumnSpec("id", ColumnType.BIGINT, false),
                new ColumnSpec("ID", ColumnType.STRING, true)
        );

        assertThatThrownBy(() -> new TableSpec(MARKET_DATA, "transactions", duplicates, null))
                .isInstanceOf(InvalidTableSpecException.class)
                .hasMessageContaining("ID");
    }

    @Test
    void rejectsBlankOrNonExistentPartitionColumn() {
        assertThatThrownBy(() -> new TableSpec(MARKET_DATA, "transactions", SAMPLE_COLUMNS, "   "))
                .isInstanceOf(InvalidTableSpecException.class);

        assertThatThrownBy(() -> new TableSpec(MARKET_DATA, "transactions", SAMPLE_COLUMNS, "non_existent"))
                .isInstanceOf(InvalidTableSpecException.class)
                .hasMessageContaining("non_existent");
    }

    @Test
    void defensivelyCopiesColumnsAndReturnsUnmodifiableList() {
        List<ColumnSpec> mutable = new ArrayList<>(SAMPLE_COLUMNS);
        TableSpec spec = new TableSpec(MARKET_DATA, "transactions", mutable, "date");

        mutable.add(new ColumnSpec("extra", ColumnType.STRING, true));

        assertThat(spec.columns()).hasSize(3);
        assertThatThrownBy(() -> spec.columns().add(new ColumnSpec("mutated", ColumnType.INT, true)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
