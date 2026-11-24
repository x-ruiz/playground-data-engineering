package x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.inmemory.InMemoryCatalog;
import org.junit.jupiter.api.Test;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcebergCatalogFactoryTest {

    private static final CatalogModel DATA_LAKE = new CatalogModel("data_lake");

    @Test
    void tc001DiscoversBuiltInProvidersAndLoadsInMemoryCatalog() {
        assertThat(IcebergCatalogFactory.availableBackends())
                .containsExactly("generic", "in_memory");

        Catalog catalog = IcebergCatalogFactory.loadCatalog("in_memory", DATA_LAKE, Map.of());
        assertThat(catalog).isInstanceOf(InMemoryCatalog.class);
        assertThat(catalog).isInstanceOf(SupportsNamespaces.class);
        assertThat(catalog.name()).isEqualTo("data_lake");

        Catalog trimmedCaseInsensitive = IcebergCatalogFactory.loadCatalog(" IN_MEMORY ", DATA_LAKE, null);
        assertThat(trimmedCaseInsensitive.name()).isEqualTo("data_lake");
    }

    @Test
    void tc002GenericCatalogProviderLoadsValidPropertiesAndRejectsInvalidInputs() {
        Catalog catalog = IcebergCatalogFactory.loadCatalog(
                "generic",
                DATA_LAKE,
                Map.of(
                        CatalogProperties.CATALOG_IMPL, InMemoryCatalog.class.getName(),
                        CatalogProperties.WAREHOUSE_LOCATION, "memory://custom"
                )
        );
        assertThat(catalog).isInstanceOf(InMemoryCatalog.class);
        assertThat(catalog.name()).isEqualTo("data_lake");

        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog("generic", null, Map.of("k", "v")))
                .isInstanceOf(InvalidCatalogModelException.class);
        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog("generic", DATA_LAKE, null))
                .isInstanceOf(CatalogOperationException.class);
        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog("generic", DATA_LAKE, Map.of()))
                .isInstanceOf(CatalogOperationException.class);

        Map<String, String> withNullValue = new HashMap<>();
        withNullValue.put("uri", null);
        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog("generic", DATA_LAKE, withNullValue))
                .isInstanceOf(CatalogOperationException.class);

        Map<String, String> withNullKey = new HashMap<>();
        withNullKey.put(null, "value");
        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog("in_memory", DATA_LAKE, withNullKey))
                .isInstanceOf(CatalogOperationException.class);

        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog(
                "generic",
                DATA_LAKE,
                Map.of(CatalogUtil.ICEBERG_CATALOG_TYPE, "unknown_engine")
        ))
                .isInstanceOf(CatalogOperationException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void tc003RejectsNullBlankUnknownAndDuplicateBackends() {
        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog(null, DATA_LAKE, Map.of()))
                .isInstanceOf(CatalogOperationException.class);
        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog("   ", DATA_LAKE, Map.of()))
                .isInstanceOf(CatalogOperationException.class);

        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog("gcp_biglake", DATA_LAKE, Map.of()))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining("gcp_biglake")
                .hasMessageContaining("in_memory")
                .hasMessageContaining("generic");

        IcebergCatalogProvider dup1 = new InMemoryCatalogProvider();
        IcebergCatalogProvider dup2 = new InMemoryCatalogProvider();
        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog(
                "in_memory", DATA_LAKE, Map.of(), List.of(dup1, dup2)))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining("Duplicate");
    }
}
