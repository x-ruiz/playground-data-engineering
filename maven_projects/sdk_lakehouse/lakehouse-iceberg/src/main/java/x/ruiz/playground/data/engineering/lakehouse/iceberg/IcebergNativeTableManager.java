package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import x.ruiz.playground.data.engineering.lakehouse.core.Table;
import x.ruiz.playground.data.engineering.lakehouse.core.TableManager;
import x.ruiz.playground.data.engineering.lakehouse.core.TableSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.WorkloadMode;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure-Java Iceberg Table Manager using official {@link Catalog} API.
 * Executes table creation, namespace management, inspection, and deletion
 * directly without requiring an Apache Spark session.
 */
public class IcebergNativeTableManager implements TableManager {
    private static final Logger logger = LogManager.getLogger(IcebergNativeTableManager.class);

    private final Catalog catalog;
    private final String catalogName;

    public IcebergNativeTableManager(Catalog catalog, String catalogName) {
        this.catalog = Objects.requireNonNull(catalog, "Catalog client must not be null");
        this.catalogName = Objects.requireNonNull(catalogName, "Catalog name must not be null");
    }

    public IcebergNativeTableManager(IcebergCatalog icebergCatalog) {
        this(icebergCatalog.loadNativeCatalog(), icebergCatalog.name());
    }

    @Override
    public void create(Table table) {
        Objects.requireNonNull(table, "Table descriptor must not be null");
        TableIdentifier identifier = TableIdentifier.of(toNamespace(table.namespace()), table.name());

        logger.info("Creating Iceberg table '{}' under catalog '{}' via native Catalog API", identifier, catalogName);

        // Ensure namespace exists
        ensureNamespaceExists(identifier.namespace());

        // Resolve Iceberg Schema
        Schema schema;
        if (table instanceof IcebergTable it && it.icebergSchema() != null && !it.icebergSchema().columns().isEmpty()) {
            schema = it.icebergSchema();
        } else if (table.schema() != null && !table.schema().trim().isEmpty()) {
            schema = IcebergSchemaParser.parse(table.schema());
        } else {
            throw new IllegalArgumentException("Cannot create table without a valid schema definition");
        }

        // Build PartitionSpec
        PartitionSpec partitionSpec = IcebergPartitionSpecHelper.buildPartitionSpec(schema, table.partitionColumns());

        // Configure table properties
        Map<String, String> properties = new HashMap<>();

        // Apply workload-specific defaults
        WorkloadMode mode = resolveWorkloadMode(table);
        if (mode == WorkloadMode.STREAMING) {
            properties.put(TableProperties.FORMAT_VERSION, "2");
            properties.put("write.distribution-mode", "fanout");
            properties.put("commit.manifest.min-count-to-merge", "4");
            properties.put("history.expire.max-snapshot-age-ms", "86400000"); // 1 day
        } else {
            properties.put(TableProperties.FORMAT_VERSION, "2");
            properties.put("write.distribution-mode", "hash");
        }

        // Overlay user-defined properties
        if (table.properties() != null) {
            properties.putAll(table.properties());
        }

        var tableBuilder = catalog.buildTable(identifier, schema)
                .withPartitionSpec(partitionSpec)
                .withProperties(properties);

        if (table.location() != null) {
            tableBuilder.withLocation(table.location());
        }

        if (catalog.tableExists(identifier)) {
            logger.warn("Table '{}' already exists in catalog '{}'. Skipping creation.", identifier, catalogName);
            return;
        }

        org.apache.iceberg.Table createdTable = tableBuilder.create();
        logger.info("Successfully created Iceberg table '{}' at location '{}' with format v{}",
                identifier, createdTable.location(),
                createdTable.properties().getOrDefault(TableProperties.FORMAT_VERSION, "2"));
    }

    @Override
    public boolean exists(String namespace, String tableName) {
        TableIdentifier identifier = TableIdentifier.of(toNamespace(namespace), tableName);
        return catalog.tableExists(identifier);
    }

    @Override
    public void drop(String namespace, String tableName, boolean purgeData) {
        TableIdentifier identifier = TableIdentifier.of(toNamespace(namespace), tableName);
        logger.info("Dropping Iceberg table '{}' (purgeData={}) from catalog '{}'", identifier, purgeData, catalogName);
        boolean dropped = catalog.dropTable(identifier, purgeData);
        if (dropped) {
            logger.info("Successfully dropped table '{}'", identifier);
        } else {
            logger.warn("Table '{}' was not dropped (may not exist)", identifier);
        }
    }

    @Override
    public List<String> listTables(String namespace) {
        Namespace ns = toNamespace(namespace);
        return catalog.listTables(ns).stream()
                .map(TableIdentifier::name)
                .toList();
    }

    @Override
    public void createNamespace(String namespace, Map<String, String> properties) {
        Namespace ns = toNamespace(namespace);
        if (catalog instanceof SupportsNamespaces sn) {
            if (!sn.namespaceExists(ns)) {
                logger.info("Creating namespace '{}' in catalog '{}'", ns, catalogName);
                sn.createNamespace(ns, properties != null ? properties : Collections.emptyMap());
            }
        }
    }

    @Override
    public boolean namespaceExists(String namespace) {
        Namespace ns = toNamespace(namespace);
        if (catalog instanceof SupportsNamespaces sn) {
            return sn.namespaceExists(ns);
        }
        return false;
    }

    @Override
    public void dropNamespace(String namespace) {
        Namespace ns = toNamespace(namespace);
        if (catalog instanceof SupportsNamespaces sn) {
            logger.info("Dropping namespace '{}' from catalog '{}'", ns, catalogName);
            sn.dropNamespace(ns);
        }
    }

    @Override
    public List<String> listNamespaces() {
        if (catalog instanceof SupportsNamespaces sn) {
            return sn.listNamespaces().stream()
                    .map(Namespace::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    public Catalog getUnderlyingCatalog() {
        return catalog;
    }

    private void ensureNamespaceExists(Namespace namespace) {
        if (catalog instanceof SupportsNamespaces sn) {
            if (!sn.namespaceExists(namespace)) {
                logger.info("Namespace '{}' does not exist. Auto-creating namespace in catalog '{}'", namespace, catalogName);
                sn.createNamespace(namespace);
            }
        }
    }

    private Namespace toNamespace(String namespaceStr) {
        if (namespaceStr == null || namespaceStr.trim().isEmpty()) {
            return Namespace.empty();
        }
        return Namespace.of(namespaceStr.split("\\."));
    }

    private WorkloadMode resolveWorkloadMode(Table table) {
        if (table instanceof IcebergTable it) {
            return it.mode();
        }
        if (table instanceof TableSpec ts) {
            return ts.mode();
        }
        return WorkloadMode.BATCH;
    }
}
