package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import x.ruiz.playground.data.engineering.lakehouse.core.Table;
import x.ruiz.playground.data.engineering.lakehouse.core.WorkloadMode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IcebergNativeTableManagerIntegrationTest {

    private IcebergCatalog catalog;
    private IcebergNativeTableManager tableManager;
    private static final String TEST_NAMESPACE = "integration_test";
    private static final String TEST_TABLE = "sensor_events";

    @BeforeEach
    void setUp() {
        // Only run if REST catalog fixture is running locally
        assumeTrue(isRestCatalogReachable(), "Local REST catalog at http://localhost:8181 is not reachable");

        catalog = IcebergCatalog.builder(CatalogType.REST)
                .name("test_rest")
                .uri("http://localhost:8181")
                .warehouse("s3://warehouse/")
                .s3Endpoint("http://localhost:9000")
                .s3PathStyleAccess("true")
                .s3AccessKey("admin")
                .s3SecretKey("password")
                .build();

        tableManager = new IcebergNativeTableManager(catalog);
    }

    @AfterEach
    void tearDown() {
        if (tableManager != null && tableManager.exists(TEST_NAMESPACE, TEST_TABLE)) {
            tableManager.drop(TEST_NAMESPACE, TEST_TABLE, true);
        }
    }

    @Test
    void shouldCreateInspectAndDropTableViaNativeCatalog() {
        // 1. Define table
        Table table = IcebergTable.builder(TEST_TABLE)
                .namespace(TEST_NAMESPACE)
                .schema("sensor_id STRING NOT NULL, temperature DOUBLE, ts TIMESTAMP")
                .partitionBy("sensor_id")
                .mode(WorkloadMode.STREAMING)
                .property("write.format.default", "parquet")
                .build();

        // 2. Create table
        tableManager.create(table);

        // 3. Verify exists
        assertThat(tableManager.exists(TEST_NAMESPACE, TEST_TABLE)).isTrue();

        // 4. Verify listed
        List<String> tables = tableManager.listTables(TEST_NAMESPACE);
        assertThat(tables).contains(TEST_TABLE);

        // 5. Drop table
        tableManager.drop(TEST_NAMESPACE, TEST_TABLE, true);

        // 6. Verify dropped
        assertThat(tableManager.exists(TEST_NAMESPACE, TEST_TABLE)).isFalse();
    }

    private boolean isRestCatalogReachable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(1000))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8181/v1/config"))
                    .timeout(Duration.ofMillis(1000))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
