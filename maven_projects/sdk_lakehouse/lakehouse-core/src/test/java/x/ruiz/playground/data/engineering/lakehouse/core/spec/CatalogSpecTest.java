package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogSpecException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogSpecTest {

    @Test
    void implementsLakehouseSpecAndReturnsNameAsFullyQualifiedName() {
        CatalogSpec catalog = new CatalogSpec("data_lake");

        assertThat(catalog).isInstanceOf(LakehouseSpec.class);
        assertThat(catalog.name()).isEqualTo("data_lake");
        assertThat(catalog.fullyQualifiedName()).isEqualTo("data_lake");
    }

    @Test
    void allowsLeadingUnderscoreAndDigitsAfterFirstCharacter() {
        CatalogSpec catalog = new CatalogSpec("_lake_2026");

        assertThat(catalog.fullyQualifiedName()).isEqualTo("_lake_2026");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "data.lake", "data lake", "1catalog", "bad-name", "name$"})
    void rejectsInvalidCatalogNames(String badName) {
        assertThatThrownBy(() -> new CatalogSpec(badName))
                .isInstanceOf(InvalidCatalogSpecException.class)
                .isInstanceOf(LakehouseException.class);
    }
}
