package x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog;

import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.catalog.Catalog;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;

import java.util.HashMap;
import java.util.Map;

/**
 * Built-in {@link IcebergCatalogProvider} for arbitrary Iceberg catalog configurations
 * (backend identifier {@code "generic"}).
 *
 * <p>Passes validated caller properties directly to
 * {@link CatalogUtil#buildIcebergCatalog(String, Map, Object)}, enabling any Iceberg catalog
 * implementation on the classpath (such as Polaris, Nessie, Hive, or JDBC) without requiring a
 * custom plugin JAR.
 */
public final class GenericCatalogProvider implements IcebergCatalogProvider {

    /** Backend identifier registered with {@link java.util.ServiceLoader}. */
    public static final String BACKEND_TYPE = "generic";

    /**
     * Public no-arg constructor required by {@link java.util.ServiceLoader}.
     */
    public GenericCatalogProvider() {
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

        try {
            return CatalogUtil.buildIcebergCatalog(catalogModel.name(), copy, null);
        } catch (RuntimeException e) {
            throw new CatalogOperationException(
                    "Failed to initialize generic Iceberg catalog '" + catalogModel.name() + "'", e);
        }
    }
}
