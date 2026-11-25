package x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.inmemory.InMemoryCatalog;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;

import java.util.HashMap;
import java.util.Map;

/**
 * Built-in {@link IcebergCatalogProvider} for Iceberg's {@link InMemoryCatalog}
 * (backend identifier {@code "in_memory"}), ideal for deterministic unit tests and local sandboxes.
 */
public final class InMemoryCatalogProvider implements IcebergCatalogProvider {

    /** Backend identifier registered with {@link java.util.ServiceLoader}. */
    public static final String BACKEND_TYPE = "in_memory";

    private static final String DEFAULT_WAREHOUSE = "memory://warehouse";

    /**
     * Public no-arg constructor required by {@link java.util.ServiceLoader}.
     */
    public InMemoryCatalogProvider() {
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

        Map<String, String> resolved = new HashMap<>();
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    throw new CatalogOperationException("Catalog properties must not contain null keys or values");
                }
                resolved.put(entry.getKey(), entry.getValue());
            }
        }
        resolved.remove(CatalogUtil.ICEBERG_CATALOG_TYPE);
        resolved.put(CatalogProperties.CATALOG_IMPL, InMemoryCatalog.class.getName());
        resolved.putIfAbsent(CatalogProperties.WAREHOUSE_LOCATION, DEFAULT_WAREHOUSE);

        try {
            return CatalogUtil.buildIcebergCatalog(catalogModel.name(), resolved, null);
        } catch (RuntimeException e) {
            throw new CatalogOperationException(
                    "Failed to initialize in-memory Iceberg catalog '" + catalogModel.name() + "'", e);
        }
    }
}
