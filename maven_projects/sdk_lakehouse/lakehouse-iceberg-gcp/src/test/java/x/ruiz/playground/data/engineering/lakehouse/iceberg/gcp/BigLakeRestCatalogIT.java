package x.ruiz.playground.data.engineering.lakehouse.iceberg.gcp;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.iceberg.IcebergNamespaceManager;
import x.ruiz.playground.data.engineering.lakehouse.iceberg.IcebergTableManager;
import x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in live integration test targeting Google Cloud BigLake Iceberg REST Catalog.
 *
 * <p>Skipped automatically during normal {@code mvn test} runs; execute explicitly with active
 * Google Application Default Credentials (ADC):
 * <pre>{@code
 * mvn test -pl lakehouse-iceberg-gcp -am \
 *   -Dtest=BigLakeRestCatalogIT \
 *   -Dsurefire.failIfNoSpecifiedTests=false \
 *   -Dbiglake.warehouse=gs://your-bucket/ \
 *   -Dbiglake.project=your-gcp-project-id
 * }</pre>
 */
@Tag("integration")
class BigLakeRestCatalogIT {

    @Test
    void tc010CreatesNamespaceAndPartitionedTableAgainstLiveBigLakeRestCatalog() throws Exception {
        String warehouse = requireSetting("biglake.warehouse", "BIGLAKE_WAREHOUSE");
        String project = requireSetting("biglake.project", "BIGLAKE_PROJECT");
        String catalogName = System.getProperty("biglake.catalog", "data_lake");
        String uri = System.getProperty("biglake.uri");

        Map<String, String> props = new HashMap<>();
        props.put(CatalogProperties.WAREHOUSE_LOCATION, warehouse);
        props.put(GcpBigLakeCatalogProvider.GCP_PROJECT_PROPERTY, project);
        if (uri != null && !uri.isBlank()) {
            props.put(CatalogProperties.URI, uri);
        }

        Catalog catalog = IcebergCatalogFactory.loadCatalog(
                GcpBigLakeCatalogProvider.BACKEND_TYPE,
                new CatalogModel(catalogName),
                props
        );

        try {
            IcebergNamespaceManager namespaceManager = new IcebergNamespaceManager(catalog);
            IcebergTableManager tableManager = new IcebergTableManager(catalog);

            Path namespaceYaml = resourcePath("/namespace.yaml");
            Path tableYaml = resourcePath("/table.yaml");

            namespaceManager.createNamespace(namespaceYaml);
            namespaceManager.createNamespace(namespaceYaml);
            assertThat(namespaceManager.namespaceExists(namespaceYaml)).isTrue();

            tableManager.createTable(tableYaml);
            tableManager.createTable(tableYaml);

            TableIdentifier tableId = TableIdentifier.of(Namespace.of("market_data"), "transactions");
            assertThat(catalog.tableExists(tableId)).isTrue();
            Table table = catalog.loadTable(tableId);
            assertThat(table.schema().findField("amount").type()).isEqualTo(Types.DecimalType.of(38, 9));
            assertThat(table.spec().isPartitioned()).isTrue();
        } finally {
            if (catalog instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    private static String requireSetting(String sysProp, String envVar) {
        String value = System.getProperty(sysProp, System.getenv(envVar));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Set -D" + sysProp + "=... or environment variable " + envVar + " to run BigLakeRestCatalogIT");
        }
        return value;
    }

    private static Path resourcePath(String resource) throws Exception {
        return Path.of(Objects.requireNonNull(
                BigLakeRestCatalogIT.class.getResource(resource),
                "Missing test resource: " + resource
        ).toURI());
    }
}
