package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.CatalogOperationException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.LakehouseException;
import x.ruiz.playground.data.engineering.lakehouse.core.exception.NamespaceNotFoundException;
import x.ruiz.playground.data.engineering.lakehouse.core.manager.TableManager;
import x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel;
import x.ruiz.playground.data.engineering.lakehouse.core.model.TableModel;

/**
 * Cloud-agnostic Apache Iceberg implementation of {@link TableManager} backed by a single
 * platform-provisioned Iceberg {@link Catalog}.
 *
 * <p>Validates exact catalog-name identity and namespace existence before creating tables, never
 * silently creates missing catalogs or namespaces, and enforces idempotent {@code CREATE TABLE IF NOT EXISTS}
 * semantics (including concurrent {@link AlreadyExistsException} races).
 */
public final class IcebergTableManager extends TableManager {

    private static final Logger LOGGER = LogManager.getLogger(IcebergTableManager.class);

    private final Catalog catalog;
    private final SupportsNamespaces supportsNamespaces;

    /**
     * Constructs an {@code IcebergTableManager} bound to a single Iceberg {@link Catalog}.
     *
     * @param catalog non-null initialized Iceberg catalog with a non-blank name implementing
     *                {@link SupportsNamespaces}
     * @throws CatalogOperationException if {@code catalog} is null, has a null/blank name, or does
     *                                   not implement {@link SupportsNamespaces}
     */
    public IcebergTableManager(Catalog catalog) {
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
    protected void doCreateTable(TableModel model) {
        verifyCatalogIdentity(model.catalog());
        Namespace namespace = IcebergTypeMapper.toIcebergNamespace(model.namespace());
        TableIdentifier tableId = IcebergTypeMapper.toIcebergTableIdentifier(model);

        try {
            if (!supportsNamespaces.namespaceExists(namespace)) {
                throw new NamespaceNotFoundException(model.namespace());
            }
            if (catalog.tableExists(tableId)) {
                LOGGER.info("Table already exists (no-op): {}", model.fullyQualifiedName());
                return;
            }
            Schema schema = IcebergTypeMapper.toIcebergSchema(model.columns());
            PartitionSpec partitionSpec = IcebergTypeMapper.toPartitionSpec(schema, model);
            catalog.createTable(tableId, schema, partitionSpec);
            LOGGER.info("Created Iceberg table: {}", model.fullyQualifiedName());
        } catch (AlreadyExistsException e) {
            LOGGER.info("Table created concurrently (idempotent no-op): {}", model.fullyQualifiedName());
        } catch (NoSuchNamespaceException e) {
            throw new NamespaceNotFoundException(model.namespace(), e);
        } catch (LakehouseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CatalogOperationException(
                    "Failed to create Iceberg table '" + model.fullyQualifiedName() + "'", e);
        }
    }

    private void verifyCatalogIdentity(CatalogModel expectedCatalog) {
        if (!catalog.name().equals(expectedCatalog.name())) {
            throw new CatalogNotFoundException(expectedCatalog);
        }
    }
}
