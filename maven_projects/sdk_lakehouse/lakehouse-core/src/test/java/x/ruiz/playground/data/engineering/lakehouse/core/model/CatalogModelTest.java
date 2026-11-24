package x.ruiz.playground.data.engineering.lakehouse.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogModelTest {

    @Test
    void implementsLakehouseModelAndReturnsNameAsFullyQualifiedName() {
        CatalogModel catalog = new CatalogModel("data_lake");

        assertThat(catalog).isInstanceOf(LakehouseModel.class);
        assertThat(catalog.name()).isEqualTo("data_lake");
        assertThat(catalog.fullyQualifiedName()).isEqualTo("data_lake");
    }

    @Test
    void allowsLeadingUnderscoreAndDigitsAfterFirstCharacter() {
        CatalogModel catalog = new CatalogModel("_lake_2026");

        assertThat(catalog.fullyQualifiedName()).isEqualTo("_lake_2026");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "data.lake", "data lake", "1catalog", "bad-name", "name$"})
    void rejectsInvalidCatalogNames(String badName) {
        assertThatThrownBy(() -> new CatalogModel(badName))
                .isInstanceOf(InvalidCatalogModelException.class)
                .isInstanceOf(LakehouseException.class);
    }
}
