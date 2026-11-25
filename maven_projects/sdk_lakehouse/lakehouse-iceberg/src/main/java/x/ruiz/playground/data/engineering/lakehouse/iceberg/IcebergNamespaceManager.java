package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;
import x.ruiz.playground.data.engineering.lakehouse.core.manager.NamespaceManager;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel;

/**
 * Cloud-agnostic Apache Iceberg implementation of {@link NamespaceManager} backed by a single
 * platform-provisioned Iceberg {@link Catalog}.
 *
 * <p>Validates exact catalog-name identity on every call to prevent cross-environment mistakes,
 * enforces idempotent namespace creation (including concurrent {@link AlreadyExistsException} races),
 * and never auto-creates missing catalogs.
 */
public final class IcebergNamespaceManager extends NamespaceManager {

    private static final Logger LOGGER = LogManager.getLogger(IcebergNamespaceManager.class);

    private final Catalog catalog;
    private final SupportsNamespaces supportsNamespaces;

    /**
     * Constructs an {@code IcebergNamespaceManager} bound to a single Iceberg {@link Catalog}.
     *
     * @param catalog non-null initialized Iceberg catalog with a non-blank name implementing
     *                {@link SupportsNamespaces}
     * @throws CatalogOperationException if {@code catalog} is null, has a null/blank name, or does
     *                                   not implement {@link SupportsNamespaces}
     */
    public IcebergNamespaceManager(Catalog catalog) {
        if (catalog == null) {
            throw new CatalogOperationException("Iceberg catalog must not be null");
        }
        if (catalog.name() == null || catalog.name().isBlank()) {
            throw new CatalogOperationException("Iceberg catalog name must not be null or blank");
        }
        if (!(catalog instanceof SupportsNamespaces nsCatalog)) {
            throw new CatalogOperationException(
                    "Iceberg catalog '" + catalog.name() + "' does not implement SupportsNamespaces");
        }
        this.catalog = catalog;
        this.supportsNamespaces = nsCatalog;
    }

    @Override
    protected void doCreateNamespace(NamespaceModel model) {
        verifyCatalogIdentity(model.catalog());
        Namespace namespace = IcebergTypeMapper.toIcebergNamespace(model);
        try {
            if (supportsNamespaces.namespaceExists(namespace)) {
                LOGGER.info("Namespace already exists (no-op): {}", model.fullyQualifiedName());
                return;
            }
            supportsNamespaces.createNamespace(namespace);
            LOGGER.info("Created Iceberg namespace: {}", model.fullyQualifiedName());
        } catch (AlreadyExistsException e) {
            LOGGER.info("Namespace created concurrently (idempotent no-op): {}", model.fullyQualifiedName());
        } catch (LakehouseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CatalogOperationException(
                    "Failed to create Iceberg namespace '" + model.fullyQualifiedName() + "'", e);
        }
    }

    @Override
    protected boolean doNamespaceExists(NamespaceModel model) {
        verifyCatalogIdentity(model.catalog());
        Namespace namespace = IcebergTypeMapper.toIcebergNamespace(model);
        try {
            return supportsNamespaces.namespaceExists(namespace);
        } catch (LakehouseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CatalogOperationException(
                    "Failed to check Iceberg namespace existence for '" + model.fullyQualifiedName() + "'", e);
        }
    }

    private void verifyCatalogIdentity(CatalogModel expectedCatalog) {
        if (!catalog.name().equals(expectedCatalog.name())) {
            throw new CatalogNotFoundException(expectedCatalog);
        }
    }
}
