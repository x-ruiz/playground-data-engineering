package x.ruiz.playground.data.engineering.lakehouse.iceberg.gcp;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.gcp.auth.GoogleAuthManager;
import org.apache.iceberg.gcp.gcs.GCSFileIO;
import org.junit.jupiter.api.Test;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogFactory;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GcpBigLakeCatalogProviderTest {

    private static final CatalogModel DATA_LAKE = new CatalogModel("data_lake");

    @Test
    void tc001ServiceLoaderDiscoversGcpPluginWithoutAwsBackends() {
        assertThat(IcebergCatalogFactory.availableBackends())
                .containsExactly("gcp_biglake", "generic", "in_memory")
                .doesNotContain("local_minio", "aws_rest");
    }

    @Test
    void tc002AppliesBigLakeDefaultsAndTranslatesGcpProjectShorthand() {
        GcpBigLakeCatalogProvider provider = new GcpBigLakeCatalogProvider();

        Map<String, String> resolved = provider.resolveProperties(Map.of(
                CatalogProperties.WAREHOUSE_LOCATION, "gs://my-lakehouse-warehouse/",
                GcpBigLakeCatalogProvider.GCP_PROJECT_PROPERTY, "my-gcp-project"
        ));

        assertThat(resolved)
                .containsEntry(CatalogUtil.ICEBERG_CATALOG_TYPE, CatalogUtil.ICEBERG_CATALOG_TYPE_REST)
                .containsEntry(CatalogProperties.URI, GcpBigLakeCatalogProvider.DEFAULT_BIGLAKE_URI)
                .containsEntry(CatalogProperties.WAREHOUSE_LOCATION, "gs://my-lakehouse-warehouse/")
                .containsEntry(CatalogProperties.FILE_IO_IMPL, GCSFileIO.class.getName())
                .containsEntry(GcpBigLakeCatalogProvider.REST_AUTH_TYPE, GoogleAuthManager.class.getName())
                .containsEntry(
                        GcpBigLakeCatalogProvider.HEADER_ACCESS_DELEGATION,
                        GcpBigLakeCatalogProvider.DEFAULT_ACCESS_DELEGATION)
                .containsEntry(GcpBigLakeCatalogProvider.HEADER_USER_PROJECT, "my-gcp-project")
                .doesNotContainKey(GcpBigLakeCatalogProvider.GCP_PROJECT_PROPERTY);
    }

    @Test
    void tc003PreservesCallerOverridesAndExtraAuthProperties() {
        GcpBigLakeCatalogProvider provider = new GcpBigLakeCatalogProvider();

        Map<String, String> resolved = provider.resolveProperties(Map.of(
                CatalogProperties.CATALOG_IMPL, "should.be.stripped",
                CatalogProperties.WAREHOUSE_LOCATION, "gs://prod-bucket/",
                CatalogProperties.URI, "https://us-central1-biglake.googleapis.com/iceberg/v1/restcatalog",
                GcpBigLakeCatalogProvider.GCP_PROJECT_PROPERTY, "prod-proj",
                GcpBigLakeCatalogProvider.HEADER_USER_PROJECT, "prod-proj",
                GcpBigLakeCatalogProvider.REST_AUTH_TYPE, "none",
                GcpBigLakeCatalogProvider.HEADER_ACCESS_DELEGATION, "remote-signing",
                "gcp.auth.credentials-path", "/var/secrets/sa.json",
                "gcp.auth.scopes", "https://www.googleapis.com/auth/cloud-platform"
        ));

        assertThat(resolved)
                .containsEntry(CatalogUtil.ICEBERG_CATALOG_TYPE, CatalogUtil.ICEBERG_CATALOG_TYPE_REST)
                .containsEntry(CatalogProperties.URI, "https://us-central1-biglake.googleapis.com/iceberg/v1/restcatalog")
                .containsEntry(GcpBigLakeCatalogProvider.HEADER_USER_PROJECT, "prod-proj")
                .containsEntry(GcpBigLakeCatalogProvider.REST_AUTH_TYPE, "none")
                .containsEntry(GcpBigLakeCatalogProvider.HEADER_ACCESS_DELEGATION, "remote-signing")
                .containsEntry("gcp.auth.credentials-path", "/var/secrets/sa.json")
                .containsEntry("gcp.auth.scopes", "https://www.googleapis.com/auth/cloud-platform")
                .doesNotContainKey(CatalogProperties.CATALOG_IMPL)
                .doesNotContainKey(GcpBigLakeCatalogProvider.GCP_PROJECT_PROPERTY);
    }

    @Test
    void tc004RejectsNullModelMissingKeysAndConflictingProjectIds() {
        GcpBigLakeCatalogProvider provider = new GcpBigLakeCatalogProvider();

        assertThatThrownBy(() -> provider.loadCatalog(null, Map.of()))
                .isInstanceOf(InvalidCatalogModelException.class);
        assertThatThrownBy(() -> provider.resolveProperties(null))
                .isInstanceOf(CatalogOperationException.class);
        assertThatThrownBy(() -> provider.resolveProperties(Map.of()))
                .isInstanceOf(CatalogOperationException.class);

        Map<String, String> withNullValue = new HashMap<>();
        withNullValue.put(CatalogProperties.WAREHOUSE_LOCATION, null);
        assertThatThrownBy(() -> provider.resolveProperties(withNullValue))
                .isInstanceOf(CatalogOperationException.class);

        assertThatThrownBy(() -> provider.resolveProperties(Map.of(
                GcpBigLakeCatalogProvider.GCP_PROJECT_PROPERTY, "my-proj"
        )))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining(CatalogProperties.WAREHOUSE_LOCATION);

        assertThatThrownBy(() -> provider.resolveProperties(Map.of(
                CatalogProperties.WAREHOUSE_LOCATION, "gs://my-bucket/"
        )))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining(GcpBigLakeCatalogProvider.GCP_PROJECT_PROPERTY)
                .hasMessageContaining(GcpBigLakeCatalogProvider.HEADER_USER_PROJECT);

        assertThatThrownBy(() -> provider.resolveProperties(Map.of(
                CatalogProperties.WAREHOUSE_LOCATION, "gs://my-bucket/",
                GcpBigLakeCatalogProvider.GCP_PROJECT_PROPERTY, "proj-a",
                GcpBigLakeCatalogProvider.HEADER_USER_PROJECT, "proj-b"
        )))
                .isInstanceOf(CatalogOperationException.class)
                .hasMessageContaining("Conflicting");
    }

    @Test
    void tc005WrapsUnderlyingCatalogLoadFailureInCatalogOperationException() {
        assertThatThrownBy(() -> IcebergCatalogFactory.loadCatalog(
                GcpBigLakeCatalogProvider.BACKEND_TYPE,
                DATA_LAKE,
                Map.of(
                        CatalogProperties.URI, "http://127.0.0.1:1",
                        CatalogProperties.WAREHOUSE_LOCATION, "gs://my-bucket/",
                        GcpBigLakeCatalogProvider.GCP_PROJECT_PROPERTY, "my-proj",
                        GcpBigLakeCatalogProvider.REST_AUTH_TYPE, "none"
                )
        ))
                .isInstanceOf(CatalogOperationException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
