package x.ruiz.playground.data.engineering.lakehouse.iceberg.aws;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.aws.s3.S3FileIO;
import org.apache.iceberg.catalog.Catalog;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link IcebergCatalogProvider} for AWS-backed Iceberg REST catalogs
 * (backend identifier {@code "aws_rest"}).
 *
 * <p>Requires {@code uri} and {@code warehouse}, enforces {@code type=rest}, defaults
 * {@code io-impl} to {@link S3FileIO}, enables {@code rest.sigv4-enabled=true} by default whenever
 * {@code rest.signing-region} is provided (unless explicitly overridden), and preserves additional
 * caller properties.
 */
public final class AwsRestCatalogProvider implements IcebergCatalogProvider {

    /** Backend identifier registered with {@link java.util.ServiceLoader}. */
    public static final String BACKEND_TYPE = "aws_rest";

    static final String REST_SIGV4_ENABLED = "rest.sigv4-enabled";
    static final String REST_SIGNING_REGION = "rest.signing-region";

    /**
     * Public no-arg constructor required by {@link java.util.ServiceLoader}.
     */
    public AwsRestCatalogProvider() {
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
                    "Failed to initialize AWS REST Iceberg catalog '" + catalogModel.name() + "'", e);
        }
    }

    /**
     * Validates required AWS REST properties and applies S3/SigV4 defaults without opening any network connection.
     */
    Map<String, String> resolveProperties(Map<String, String> properties) {
        Map<String, String> resolved = copyAndValidateNonNull(properties);
        requireNonBlank(resolved, CatalogProperties.URI);
        requireNonBlank(resolved, CatalogProperties.WAREHOUSE_LOCATION);

        resolved.remove(CatalogProperties.CATALOG_IMPL);
        resolved.put(CatalogUtil.ICEBERG_CATALOG_TYPE, CatalogUtil.ICEBERG_CATALOG_TYPE_REST);
        resolved.putIfAbsent(CatalogProperties.FILE_IO_IMPL, S3FileIO.class.getName());

        String signingRegion = resolved.get(REST_SIGNING_REGION);
        if (signingRegion != null && !signingRegion.isBlank()) {
            resolved.putIfAbsent(REST_SIGV4_ENABLED, "true");
        }
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
