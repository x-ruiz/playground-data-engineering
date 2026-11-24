package x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog;

import org.apache.iceberg.catalog.Catalog;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidCatalogModelException;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;

/**
 * Stateless {@link ServiceLoader}-backed factory that discovers {@link IcebergCatalogProvider}
 * plugins on the classpath and loads an Apache Iceberg {@link Catalog} for a requested backend.
 *
 * <p>Pipelines can run a single codebase across environments by deploying only the target cloud
 * plugin JAR (for example, {@code lakehouse-iceberg-aws} or {@code lakehouse-iceberg-gcp}) and
 * selecting the backend via environment configuration.
 */
public final class IcebergCatalogFactory {

    private IcebergCatalogFactory() {
    }

    /**
     * Discovers the {@link IcebergCatalogProvider} matching {@code backendType} and loads an
     * initialized Apache Iceberg {@link Catalog}.
     *
     * <p>Callers own closing the returned catalog if it implements {@link AutoCloseable}.
     *
     * @param backendType  backend identifier (trimmed and matched case-insensitively)
     * @param catalogModel non-null catalog model providing the catalog name
     * @param properties   backend configuration properties
     * @return initialized Apache Iceberg {@link Catalog}
     * @throws InvalidCatalogModelException if {@code catalogModel} is null
     * @throws CatalogOperationException    if {@code backendType} is null/blank/unknown, duplicate
     *                                      providers are registered for the same backend, or catalog
     *                                      initialization fails
     */
    public static Catalog loadCatalog(
            String backendType,
            CatalogModel catalogModel,
            Map<String, String> properties) {
        return loadCatalog(backendType, catalogModel, properties, ServiceLoader.load(IcebergCatalogProvider.class));
    }

    /**
     * Returns an unmodifiable, sorted set of normalized backend identifiers discovered on the
     * current classpath via {@link ServiceLoader}.
     *
     * @return sorted set of available backend types
     * @throws CatalogOperationException if duplicate or invalid provider registrations are detected
     */
    public static Set<String> availableBackends() {
        return Collections.unmodifiableSet(
                indexProviders(ServiceLoader.load(IcebergCatalogProvider.class)).keySet());
    }

    /**
     * Package-private overload enabling deterministic unit tests with custom provider sets.
     */
    static Catalog loadCatalog(
            String backendType,
            CatalogModel catalogModel,
            Map<String, String> properties,
            Iterable<IcebergCatalogProvider> providers) {
        if (catalogModel == null) {
            throw new InvalidCatalogModelException("Catalog model must not be null");
        }
        if (backendType == null || backendType.isBlank()) {
            throw new CatalogOperationException("Catalog backend type must not be null or blank");
        }

        Map<String, IcebergCatalogProvider> indexed = indexProviders(providers);
        String normalizedBackend = normalizeBackend(backendType);
        IcebergCatalogProvider provider = indexed.get(normalizedBackend);
        if (provider == null) {
            throw new CatalogOperationException(
                    "No IcebergCatalogProvider found for backend '" + backendType
                            + "'. Available backends on classpath: " + indexed.keySet());
        }
        return provider.loadCatalog(catalogModel, properties);
    }

    private static Map<String, IcebergCatalogProvider> indexProviders(Iterable<IcebergCatalogProvider> providers) {
        Map<String, IcebergCatalogProvider> indexed = new TreeMap<>();
        for (IcebergCatalogProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            String rawType = provider.backendType();
            if (rawType == null || rawType.isBlank()) {
                throw new CatalogOperationException(
                        "IcebergCatalogProvider " + provider.getClass().getName()
                                + " returned a null or blank backendType()");
            }
            String normalized = normalizeBackend(rawType);
            IcebergCatalogProvider existing = indexed.putIfAbsent(normalized, provider);
            if (existing != null) {
                throw new CatalogOperationException(
                        "Duplicate IcebergCatalogProvider registered for backend '" + normalized + "': "
                                + existing.getClass().getName() + " and " + provider.getClass().getName());
            }
        }
        return indexed;
    }

    private static String normalizeBackend(String backendType) {
        return backendType.trim().toLowerCase(Locale.ROOT);
    }
}
