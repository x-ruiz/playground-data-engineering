package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.types.Types;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.spark.sql.SparkSession;
import x.ruiz.playground.data.engineering.lakehouse.core.Table;
import x.ruiz.playground.data.engineering.lakehouse.core.TableManager;
import x.ruiz.playground.data.engineering.lakehouse.core.TableSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.WorkloadMode;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Spark SQL-based Iceberg Table Manager.
 * Generates dynamic DDL statements and executes them against an active SparkSession.
 */
public class SparkIcebergTableManager implements TableManager {
    private static final Logger logger = LogManager.getLogger(SparkIcebergTableManager.class);

    private final SparkSession spark;
    private final String catalogName;

    public SparkIcebergTableManager(SparkSession spark, String catalogName) {
        this.spark = Objects.requireNonNull(spark, "SparkSession must not be null");
        this.catalogName = Objects.requireNonNull(catalogName, "Catalog name must not be null");
    }

    public SparkIcebergTableManager(SparkSession spark, IcebergCatalog catalog) {
        this(spark, catalog.name());
    }

    @Override
    @SuppressWarnings("SqlSourceToSinkFlow")
    public void create(Table table) {
        create(this.spark, table);
    }

    @Override
    @SuppressWarnings("SqlSourceToSinkFlow")
    public void create(SparkSession session, Table table) {
        Objects.requireNonNull(table, "Table must not be null");
        String fullNamespace = catalogName + "." + table.namespace();
        String fullTableName = fullNamespace + "." + table.name();

        logger.info("Creating Iceberg table '{}' via Spark SQL", fullTableName);

        // 1. Create Namespace
        session.sql(String.format("CREATE NAMESPACE IF NOT EXISTS %s", fullNamespace));

        // 2. Build Columns DDL
        String columnsDdl = buildColumnsDdl(table);

        // 3. Build Partitioning clause
        StringBuilder ddlBuilder = new StringBuilder();
        ddlBuilder.append(String.format("CREATE TABLE IF NOT EXISTS %s (\n%s\n)\nUSING iceberg\n",
                fullTableName, columnsDdl));

        if (table.partitionColumns() != null && !table.partitionColumns().isEmpty()) {
            String partitionClause = String.join(", ", table.partitionColumns());
            ddlBuilder.append(String.format("PARTITIONED BY (%s)\n", partitionClause));
        }

        if (table.location() != null && !table.location().trim().isEmpty()) {
            ddlBuilder.append(String.format("LOCATION '%s'\n", table.location()));
        }

        // 4. Properties clause
        Map<String, String> properties = new HashMap<>();
        WorkloadMode mode = resolveWorkloadMode(table);
        if (mode == WorkloadMode.STREAMING) {
            properties.put(TableProperties.FORMAT_VERSION, "2");
            properties.put("write.distribution-mode", "fanout");
            properties.put("commit.manifest.min-count-to-merge", "4");
            properties.put("history.expire.max-snapshot-age-ms", "86400000");
        } else {
            properties.put(TableProperties.FORMAT_VERSION, "2");
            properties.put("write.distribution-mode", "hash");
        }
        if (table.properties() != null) {
            properties.putAll(table.properties());
        }

        if (!properties.isEmpty()) {
            String propsString = properties.entrySet().stream()
                    .map(e -> String.format("'%s' = '%s'", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(",\n    "));
            ddlBuilder.append(String.format("TBLPROPERTIES (\n    %s\n)\n", propsString));
        }

        String finalDdl = ddlBuilder.toString();
        logger.debug("Executing Spark SQL DDL:\n{}", finalDdl);
        session.sql(finalDdl);

        logger.info("Successfully executed DDL for table '{}'", fullTableName);
    }

    @Override
    public boolean exists(String namespace, String tableName) {
        String fullTableName = catalogName + "." + namespace + "." + tableName;
        return spark.catalog().tableExists(fullTableName);
    }

    @Override
    public void drop(String namespace, String tableName, boolean purgeData) {
        String fullTableName = catalogName + "." + namespace + "." + tableName;
        logger.info("Dropping table '{}' via Spark SQL (purge={})", fullTableName, purgeData);
        spark.sql(String.format("DROP TABLE IF EXISTS %s%s", fullTableName, purgeData ? " PURGE" : ""));
    }

    @Override
    public List<String> listTables(String namespace) {
        String fullNamespace = catalogName + "." + namespace;
        try {
            return spark.catalog().listTables(fullNamespace).collectAsList().stream()
                    .map(org.apache.spark.sql.catalog.Table::name)
                    .toList();
        } catch (org.apache.spark.sql.AnalysisException e) {
            logger.warn("Namespace '{}' not found when listing tables: {}", fullNamespace, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void createNamespace(String namespace, Map<String, String> properties) {
        String fullNamespace = catalogName + "." + namespace;
        spark.sql(String.format("CREATE NAMESPACE IF NOT EXISTS %s", fullNamespace));
    }

    @Override
    public boolean namespaceExists(String namespace) {
        String fullNamespace = catalogName + "." + namespace;
        return spark.catalog().databaseExists(fullNamespace);
    }

    @Override
    public void dropNamespace(String namespace) {
        String fullNamespace = catalogName + "." + namespace;
        spark.sql(String.format("DROP NAMESPACE IF EXISTS %s", fullNamespace));
    }

    @Override
    public List<String> listNamespaces() {
        return spark.catalog().listDatabases().collectAsList().stream()
                .map(org.apache.spark.sql.catalog.Database::name)
                .filter(name -> name.startsWith(catalogName))
                .toList();
    }

    private String buildColumnsDdl(Table table) {
        if (table instanceof IcebergTable it && it.icebergSchema() != null && !it.icebergSchema().columns().isEmpty()) {
            return formatIcebergSchemaToSql(it.icebergSchema());
        }

        if (table.schema() != null && !table.schema().trim().isEmpty()) {
            return table.schema();
        }

        throw new IllegalArgumentException("No schema or columns defined for table: " + table.name());
    }

    private String formatIcebergSchemaToSql(Schema schema) {
        return schema.columns().stream()
                .map(f -> String.format("    %s %s%s",
                        f.name(),
                        icebergTypeToSqlType(f.type()),
                        f.isRequired() ? " NOT NULL" : ""))
                .collect(Collectors.joining(",\n"));
    }

    private String icebergTypeToSqlType(org.apache.iceberg.types.Type type) {
        return switch (type.typeId()) {
            case BOOLEAN -> "BOOLEAN";
            case INTEGER -> "INT";
            case LONG -> "BIGINT";
            case FLOAT -> "FLOAT";
            case DOUBLE -> "DOUBLE";
            case DATE -> "DATE";
            case TIME -> "STRING";
            case TIMESTAMP -> ((Types.TimestampType) type).shouldAdjustToUTC() ? "TIMESTAMP" : "TIMESTAMP_NTZ";
            case STRING -> "STRING";
            case UUID -> "STRING";
            case FIXED, BINARY -> "BINARY";
            case DECIMAL -> {
                Types.DecimalType dec = (Types.DecimalType) type;
                yield String.format("DECIMAL(%d, %d)", dec.precision(), dec.scale());
            }
            default -> "STRING";
        };
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
