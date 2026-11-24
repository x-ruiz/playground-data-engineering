package x.ruiz.playground.data.engineering.lakehouse.iceberg.aws;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.aws.s3.S3FileIO;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.junit.jupiter.api.Test;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogFactory;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwsCatalogProvidersTest {

    @Test
    void tc015ServiceLoaderDiscoversBuiltInAndAwsPluginProviders() {
        assertThat(IcebergCatalogFactory.availableBackends())
                .containsExactly("aws_rest", "generic", "in_memory", "local_minio");
    }

    @Test
    void tc016LocalMinioProviderValidatesRequiredKeysAndAppliesDefaults() {
        LocalMinioCatalogProvider provider = new LocalMinioCatalogProvider();

        Map<String, String> resolved = provider.resolveProperties(Map.of(
                CatalogProperties.URI, "http://localhost:8181",
                CatalogProperties.WAREHOUSE_LOCATION, "s3://warehouse/",
                S3FileIOProperties.ENDPOINT, "http://localhost:9000",
                "s3.access-key-id", "admin"
        ));

        assertThat(resolved)
                .containsEntry(CatalogUtil.ICEBERG_CATALOG_TYPE, CatalogUtil.ICEBERG_CATALOG_TYPE_REST)
                .containsEntry(CatalogProperties.URI, "http://localhost:8181")
                .containsEntry(CatalogProperties.WAREHOUSE_LOCATION, "s3://warehouse/")
                .containsEntry(S3FileIOProperties.ENDPOINT, "http://localhost:9000")
                .containsEntry(CatalogProperties.FILE_IO_IMPL, S3FileIO.class.getName())
                .containsEntry(S3FileIOProperties.PATH_STYLE_ACCESS, "true")
                .containsEntry("s3.access-key-id", "admin");

        // Respects explicit caller override of s3.path-style-access
        Map<String, String> overridden = provider.resolveProperties(Map.of(
                CatalogProperties.URI, "http://localhost:8181",
                CatalogProperties.WAREHOUSE_LOCATION, "s3://warehouse/",
                S3FileIOProperties.ENDPOINT, "http://localhost:9000",
                S3FileIOProperties.PATH_STYLE_ACCESS, "false"
        ));
        assertThat(overridden).containsEntry(S3FileIOProperties.PATH_STYLE_ACCESS, "false");

        assertThatThrownBy(() -> provider.loadCatalog(null, Map.of()))
                .isInstanceOf(InvalidCatalogModelException.class);
        assertThatThrownBy(() -> provider.resolveProperties(null))
                .isInstanceOf(CatalogOperationException.class);
        assertThatThrownBy(() -> provider.resolveProperties(Map.of()))
                .isInstanceOf(CatalogOperationException.class);
        assertThatThrownBy(() -> provider.resolveProperties(Map.of(
                CatalogProperties.URI, "http://localhost:8181",
                CatalogProperties.WAREHOUSE_LOCATION, "s3://warehouse/"
        )))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining(S3FileIOProperties.ENDPOINT);

        Map<String, String> withNullVal = new HashMap<>();
        withNullVal.put(CatalogProperties.URI, "http://localhost:8181");
        withNullVal.put(CatalogProperties.WAREHOUSE_LOCATION, null);
        assertThatThrownBy(() -> provider.resolveProperties(withNullVal))
                .isInstanceOf(CatalogOperationException.class);
    }

    @Test
    void tc017AwsRestProviderValidatesRequiredKeysAndAppliesSigV4Defaults() {
        AwsRestCatalogProvider provider = new AwsRestCatalogProvider();

        Map<String, String> withoutSigningRegion = provider.resolveProperties(Map.of(
                CatalogProperties.URI, "https://iceberg.example.com",
                CatalogProperties.WAREHOUSE_LOCATION, "s3://prod-warehouse/"
        ));
        assertThat(withoutSigningRegion)
                .containsEntry(CatalogUtil.ICEBERG_CATALOG_TYPE, CatalogUtil.ICEBERG_CATALOG_TYPE_REST)
                .containsEntry(CatalogProperties.FILE_IO_IMPL, S3FileIO.class.getName())
                .doesNotContainKey(AwsRestCatalogProvider.REST_SIGV4_ENABLED);

        Map<String, String> withSigningRegion = provider.resolveProperties(Map.of(
                CatalogProperties.URI, "https://iceberg.example.com",
                CatalogProperties.WAREHOUSE_LOCATION, "s3://prod-warehouse/",
                AwsRestCatalogProvider.REST_SIGNING_REGION, "us-east-1"
        ));
        assertThat(withSigningRegion)
                .containsEntry(AwsRestCatalogProvider.REST_SIGV4_ENABLED, "true")
                .containsEntry(AwsRestCatalogProvider.REST_SIGNING_REGION, "us-east-1");

        Map<String, String> explicitSigV4Disabled = provider.resolveProperties(Map.of(
                CatalogProperties.URI, "https://iceberg.example.com",
                CatalogProperties.WAREHOUSE_LOCATION, "s3://prod-warehouse/",
                AwsRestCatalogProvider.REST_SIGNING_REGION, "us-east-1",
                AwsRestCatalogProvider.REST_SIGV4_ENABLED, "false"
        ));
        assertThat(explicitSigV4Disabled)
                .containsEntry(AwsRestCatalogProvider.REST_SIGV4_ENABLED, "false");

        assertThatThrownBy(() -> provider.loadCatalog(null, Map.of()))
                .isInstanceOf(InvalidCatalogModelException.class);
        assertThatThrownBy(() -> provider.resolveProperties(Map.of(
                CatalogProperties.URI, "https://iceberg.example.com"
        )))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining(CatalogProperties.WAREHOUSE_LOCATION);
        assertThatThrownBy(() -> provider.resolveProperties(Map.of(
                CatalogProperties.WAREHOUSE_LOCATION, "s3://prod-warehouse/"
        )))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining(CatalogProperties.URI);
    }
}
