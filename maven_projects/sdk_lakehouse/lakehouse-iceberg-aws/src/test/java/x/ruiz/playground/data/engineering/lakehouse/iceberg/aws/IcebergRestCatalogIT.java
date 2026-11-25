package x.ruiz.playground.data.engineering.lakehouse.iceberg.aws;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.Table;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
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
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in integration test targeting the local Docker Iceberg REST + MinIO sandbox
 * ({@code docker compose up}). Skipped automatically during normal {@code mvn test} runs;
 * execute explicitly via {@code mvn test -pl lakehouse-iceberg-aws -Dtest=IcebergRestCatalogIT}.
 */
@Tag("integration")
class IcebergRestCatalogIT {

    @Test
    void tc018CreatesNamespaceAndPartitionedTableAgainstLiveDockerRestCatalog() throws Exception {
        String restUri = System.getProperty("iceberg.rest.uri", "http://localhost:8181");
        String minioEndpoint = System.getProperty("iceberg.s3.endpoint", "http://localhost:9000");

        Catalog catalog = IcebergCatalogFactory.loadCatalog(
                LocalMinioCatalogProvider.BACKEND_TYPE,
                new CatalogModel("data_lake"),
                Map.of(
                        CatalogProperties.URI, restUri,
                        CatalogProperties.WAREHOUSE_LOCATION, "s3://warehouse/",
                        S3FileIOProperties.ENDPOINT, minioEndpoint,
                        S3FileIOProperties.ACCESS_KEY_ID, "admin",
                        S3FileIOProperties.SECRET_ACCESS_KEY, "password",
                        "client.region", "us-east-1"
                )
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

    private static Path resourcePath(String resource) throws Exception {
        return Path.of(Objects.requireNonNull(
                IcebergRestCatalogIT.class.getResource(resource),
                "Missing test resource: " + resource
        ).toURI());
    }
}
