package x.ruiz.playground.data.engineering.lakehouse.iceberg.gcp;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.gcp.auth.GoogleAuthManager;
import org.apache.iceberg.gcp.gcs.GCSFileIO;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link IcebergCatalogProvider} for Google Cloud BigLake Iceberg REST catalogs
 * (backend identifier {@code "gcp_biglake"}).
 *
 * <p>Requires {@code warehouse} and a GCP project ID supplied via either {@code gcp.project}
 * (translated to {@code header.x-goog-user-project} and removed) or
 * {@code header.x-goog-user-project}. Enforces {@code type=rest} and defaults {@code uri} to
 * {@link #DEFAULT_BIGLAKE_URI}, {@code io-impl} to {@link GCSFileIO}, {@code rest.auth.type} to
 * {@link GoogleAuthManager}, and {@code header.X-Iceberg-Access-Delegation} to
 * {@code "vended-credentials"}.
 */
public final class GcpBigLakeCatalogProvider implements IcebergCatalogProvider {

    /** Backend identifier registered with {@link java.util.ServiceLoader}. */
    public static final String BACKEND_TYPE = "gcp_biglake";

    /** Default Google Cloud BigLake Iceberg REST catalog endpoint URI. */
    public static final String DEFAULT_BIGLAKE_URI = "https://biglake.googleapis.com/iceberg/v1/restcatalog";

    /** Convenience property key translated into {@link #HEADER_USER_PROJECT}. */
    public static final String GCP_PROJECT_PROPERTY = "gcp.project";

    /** Iceberg REST header property attributing BigLake API usage and billing to a GCP project. */
    public static final String HEADER_USER_PROJECT = "header.x-goog-user-project";

    /** Iceberg REST header property requesting vended storage credentials from BigLake. */
    public static final String HEADER_ACCESS_DELEGATION = "header.X-Iceberg-Access-Delegation";

    /** Iceberg REST authentication manager configuration key. */
    static final String REST_AUTH_TYPE = "rest.auth.type";

    /** Default access delegation header value for BigLake vended credentials. */
    static final String DEFAULT_ACCESS_DELEGATION = "vended-credentials";

    /**
     * Public no-arg constructor required by {@link java.util.ServiceLoader}.
     */
    public GcpBigLakeCatalogProvider() {
    }

    @Override
    public String backendType() {
        return BACKEND_TYPE;
    }

    @Override
    public Catalog loadCatalog(CatalogModel catalogModel, Map<String, String> properties) {
        if (catalogModel == null) {
            throw new InvalidCatalogModelException("Catalog model must not be null");
        }
        Map<String, String> resolved = resolveProperties(properties);
        try {
            return CatalogUtil.buildIcebergCatalog(catalogModel.name(), resolved, null);
        } catch (RuntimeException e) {
            throw new CatalogOperationException(
                    "Failed to initialize GCP BigLake REST Iceberg catalog '" + catalogModel.name() + "'", e);
        }
    }

    /**
     * Validates required BigLake properties and applies GCP REST/GCS/GoogleAuth defaults without
     * performing any network or credential calls.
     */
    Map<String, String> resolveProperties(Map<String, String> properties) {
        Map<String, String> resolved = copyAndValidateNonNull(properties);
        requireNonBlank(resolved, CatalogProperties.WAREHOUSE_LOCATION);

        String shorthandProject = resolved.remove(GCP_PROJECT_PROPERTY);
        String headerProject = resolved.get(HEADER_USER_PROJECT);
        String normalizedShorthand = normalizeBlankToNull(shorthandProject);
        String normalizedHeader = normalizeBlankToNull(headerProject);

        if (normalizedShorthand == null && normalizedHeader == null) {
            throw new CatalogOperationException(
                    "Missing required GCP project ID: provide '" + GCP_PROJECT_PROPERTY
                            + "' or '" + HEADER_USER_PROJECT + "' for backend '" + BACKEND_TYPE + "'");
        }
        if (normalizedShorthand != null && normalizedHeader != null && !normalizedShorthand.equals(normalizedHeader)) {
            throw new CatalogOperationException(
                    "Conflicting GCP project IDs: '" + GCP_PROJECT_PROPERTY + "' ('" + normalizedShorthand
                            + "') does not match '" + HEADER_USER_PROJECT + "' ('" + normalizedHeader + "')");
        }
        resolved.put(HEADER_USER_PROJECT, normalizedHeader != null ? normalizedHeader : normalizedShorthand);

        resolved.remove(CatalogProperties.CATALOG_IMPL);
        resolved.put(CatalogUtil.ICEBERG_CATALOG_TYPE, CatalogUtil.ICEBERG_CATALOG_TYPE_REST);
        resolved.putIfAbsent(CatalogProperties.URI, DEFAULT_BIGLAKE_URI);
        resolved.putIfAbsent(CatalogProperties.FILE_IO_IMPL, GCSFileIO.class.getName());
        resolved.putIfAbsent(REST_AUTH_TYPE, GoogleAuthManager.class.getName());
        resolved.putIfAbsent(HEADER_ACCESS_DELEGATION, DEFAULT_ACCESS_DELEGATION);
        return resolved;
    }

    private static Map<String, String> copyAndValidateNonNull(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            throw new CatalogOperationException(
                    "Catalog properties must not be null or empty for backend '" + BACKEND_TYPE + "'");
        }
        Map<String, String> copy = new HashMap<>(properties.size());
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new CatalogOperationException("Catalog properties must not contain null keys or values");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private static void requireNonBlank(Map<String, String> properties, String key) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new CatalogOperationException(
                    "Missing required property '" + key + "' for backend '" + BACKEND_TYPE + "'");
        }
    }

    private static String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
