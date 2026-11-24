package x.ruiz.playground.data.engineering.lakehouse.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableModelTest {

    private static final CatalogModel DATA_LAKE = new CatalogModel("data_lake");
    private static final NamespaceModel MARKET_DATA = new NamespaceModel(DATA_LAKE, "market_data");

    private static final List<ColumnModel> SAMPLE_COLUMNS = List.of(
            new ColumnModel("date", ColumnType.DATE, false),
            new ColumnModel("id", ColumnType.BIGINT, false),
            new ColumnModel("amount", ColumnType.DECIMAL, true)
    );

    @Test
    void composesNamespaceModelAndExposesCatalogAndFqn() {
        TableModel spec = new TableModel(MARKET_DATA, "transactions", SAMPLE_COLUMNS, "date");

        assertThat(spec).isInstanceOf(LakehouseModel.class);
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
        TableModel spec = new TableModel(MARKET_DATA, "transactions", SAMPLE_COLUMNS, "DATE");

        assertThat(spec.isPartitioned()).isTrue();
        assertThat(spec.partitionColumn()).isEqualTo("date");
        assertThat(spec.partitionColumnOptional()).contains("date");
    }

    @Test
    void unpartitionedTableModelAllowsNullPartitionColumn() {
        TableModel spec = new TableModel(MARKET_DATA, "transactions", SAMPLE_COLUMNS, null);

        assertThat(spec.isPartitioned()).isFalse();
        assertThat(spec.partitionColumnOptional()).isEmpty();
    }

    @Test
    void rejectsNullNamespaceModel() {
        assertThatThrownBy(() -> new TableModel(null, "transactions", SAMPLE_COLUMNS, "date"))
                .isInstanceOf(InvalidTableModelException.class)
                .isInstanceOf(LakehouseException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "bad.table", "table name", "9table", "bad-table", "name$"})
    void rejectsInvalidTableName(String badName) {
        assertThatThrownBy(() -> new TableModel(MARKET_DATA, badName, SAMPLE_COLUMNS, null))
                .isInstanceOf(InvalidTableModelException.class)
                .isInstanceOf(LakehouseException.class);
    }

    @Test
    void rejectsNullOrEmptyColumns() {
        assertThatThrownBy(() -> new TableModel(MARKET_DATA, "transactions", null, null))
                .isInstanceOf(InvalidTableModelException.class);

        assertThatThrownBy(() -> new TableModel(MARKET_DATA, "transactions", List.of(), null))
                .isInstanceOf(InvalidTableModelException.class);
    }

    @Test
    void rejectsNullColumnEntry() {
        List<ColumnModel> withNull = new ArrayList<>();
        withNull.add(new ColumnModel("id", ColumnType.BIGINT, false));
        withNull.add(null);

        assertThatThrownBy(() -> new TableModel(MARKET_DATA, "transactions", withNull, null))
                .isInstanceOf(InvalidTableModelException.class);
    }

    @Test
    void rejectsDuplicateColumnNamesCaseInsensitively() {
        List<ColumnModel> duplicates = List.of(
                new ColumnModel("id", ColumnType.BIGINT, false),
                new ColumnModel("ID", ColumnType.STRING, true)
        );

        assertThatThrownBy(() -> new TableModel(MARKET_DATA, "transactions", duplicates, null))
                .isInstanceOf(InvalidTableModelException.class)
                .hasMessageContaining("ID");
    }

    @Test
    void rejectsBlankOrNonExistentPartitionColumn() {
        assertThatThrownBy(() -> new TableModel(MARKET_DATA, "transactions", SAMPLE_COLUMNS, "   "))
                .isInstanceOf(InvalidTableModelException.class);

        assertThatThrownBy(() -> new TableModel(MARKET_DATA, "transactions", SAMPLE_COLUMNS, "non_existent"))
                .isInstanceOf(InvalidTableModelException.class)
                .hasMessageContaining("non_existent");
    }

    @Test
    void defensivelyCopiesColumnsAndReturnsUnmodifiableList() {
        List<ColumnModel> mutable = new ArrayList<>(SAMPLE_COLUMNS);
        TableModel spec = new TableModel(MARKET_DATA, "transactions", mutable, "date");

        mutable.add(new ColumnModel("extra", ColumnType.STRING, true));

        assertThat(spec.columns()).hasSize(3);
        assertThatThrownBy(() -> spec.columns().add(new ColumnModel("mutated", ColumnType.INT, true)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
