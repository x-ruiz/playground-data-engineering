package x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog;

import org.apache.iceberg.catalog.Catalog;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;

import java.util.Map;

/**
 * Service Provider Interface (SPI) for loading an Apache Iceberg {@link Catalog} for a specific
 * backend (for example, {@code "in_memory"}, {@code "generic"}, {@code "local_minio"},
 * {@code "aws_rest"}, or {@code "gcp_biglake"}).
 *
 * <p>Implementations are packaged in core or cloud-specific plugin JARs, must expose a
 * {@code public} no-arg constructor, and register themselves in
 * {@code META-INF/services/x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogProvider}
 * for automatic discovery by {@link IcebergCatalogFactory}.
 */
public interface IcebergCatalogProvider {

    /**
     * Returns the unique backend identifier handled by this provider
     * (for example, {@code "in_memory"}, {@code "local_minio"}, or {@code "aws_rest"}).
     *
     * @return non-blank backend identifier (matched case-insensitively after trimming)
     */
    String backendType();

    /**
     * Builds and initializes an Apache Iceberg {@link Catalog} from the supplied catalog model
     * and environment properties.
     *
     * <p>Implementations must be stateless and must not retain references to created catalogs.
     *
     * @param catalogModel non-null catalog identity model whose name is assigned to the Iceberg catalog
     * @param properties   environment-supplied configuration properties for this backend
     * @return an initialized Apache Iceberg {@link Catalog}
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException
     *         if {@code catalogModel} is null
     * @throws x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException
     *         if required properties are missing/invalid or catalog initialization fails
     */
    Catalog loadCatalog(CatalogModel catalogModel, Map<String, String> properties);
}
