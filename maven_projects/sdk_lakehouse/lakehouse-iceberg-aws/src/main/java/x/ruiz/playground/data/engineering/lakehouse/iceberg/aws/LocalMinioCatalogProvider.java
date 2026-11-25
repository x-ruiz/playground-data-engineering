package x.ruiz.playground.data.engineering.lakehouse.iceberg.aws;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.aws.s3.S3FileIO;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.catalog.Catalog;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link IcebergCatalogProvider} for local Docker Iceberg REST + MinIO sandboxes
 * (backend identifier {@code "local_minio"}).
 *
 * <p>Requires {@code uri}, {@code warehouse}, and {@code s3.endpoint}, enforces {@code type=rest},
 * defaults {@code io-impl} to {@link S3FileIO} and {@code s3.path-style-access} to {@code "true"},
 * and preserves additional caller properties such as dev credentials or region settings.
 */
public final class LocalMinioCatalogProvider implements IcebergCatalogProvider {

    /** Backend identifier registered with {@link java.util.ServiceLoader}. */
    public static final String BACKEND_TYPE = "local_minio";

    /**
     * Public no-arg constructor required by {@link java.util.ServiceLoader}.
     */
    public LocalMinioCatalogProvider() {
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
                    "Failed to initialize local MinIO REST Iceberg catalog '" + catalogModel.name() + "'", e);
        }
    }

    /**
     * Validates required MinIO REST properties and applies defaults without opening any network connection.
     */
    Map<String, String> resolveProperties(Map<String, String> properties) {
        Map<String, String> resolved = copyAndValidateNonNull(properties);
        requireNonBlank(resolved, CatalogProperties.URI);
        requireNonBlank(resolved, CatalogProperties.WAREHOUSE_LOCATION);
        requireNonBlank(resolved, S3FileIOProperties.ENDPOINT);

        resolved.remove(CatalogProperties.CATALOG_IMPL);
        resolved.put(CatalogUtil.ICEBERG_CATALOG_TYPE, CatalogUtil.ICEBERG_CATALOG_TYPE_REST);
        resolved.putIfAbsent(CatalogProperties.FILE_IO_IMPL, S3FileIO.class.getName());
        resolved.putIfAbsent(S3FileIOProperties.PATH_STYLE_ACCESS, "true");
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
}
