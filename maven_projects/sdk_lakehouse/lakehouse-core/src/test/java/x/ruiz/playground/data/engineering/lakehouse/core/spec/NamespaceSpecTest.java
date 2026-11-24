package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidNamespaceSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceSpecTest {

    private static final CatalogSpec DATA_LAKE = new CatalogSpec("data_lake");

    @Test
    void composesCatalogSpecAndFormatsFullyQualifiedName() {
        NamespaceSpec spec = new NamespaceSpec(DATA_LAKE, "market_data");

        assertThat(spec).isInstanceOf(LakehouseSpec.class);
        assertThat(spec.catalog()).isEqualTo(DATA_LAKE);
        assertThat(spec.name()).isEqualTo("market_data");
        assertThat(spec.fullyQualifiedName()).isEqualTo("data_lake.market_data");
    }

    @Test
    void rejectsNullCatalogSpec() {
        assertThatThrownBy(() -> new NamespaceSpec(null, "market_data"))
                .isInstanceOf(InvalidNamespaceSpecException.class)
                .isInstanceOf(LakehouseException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "market.data", "market data", "123ns", "bad-ns", "ns$"})
    void rejectsInvalidNamespaceNames(String badName) {
        assertThatThrownBy(() -> new NamespaceSpec(DATA_LAKE, badName))
                .isInstanceOf(InvalidNamespaceSpecException.class)
                .isInstanceOf(LakehouseException.class);
    }
}
