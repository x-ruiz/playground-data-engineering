package x.ruiz.playground.data.engineering.lakehouse.core;

import org.apache.spark.sql.SparkSession;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface TableManager {
    /**
     * Creates a table using the configured catalog/engine.
     *
     * @param table the table definition to create
     */
    void create(Table table);

    /**
     * Creates a table using a specific SparkSession.
     *
     * @param spark the SparkSession to execute with
     * @param table the table definition to create
     */
    default void create(SparkSession spark, Table table) {
        create(table);
    }

    /**
     * Checks if a table exists within a given namespace.
     *
     * @param namespace the table namespace
     * @param tableName the table name
     * @return true if the table exists, false otherwise
     */
    boolean exists(String namespace, String tableName);

    /**
     * Checks if a table exists.
     *
     * @param table the table descriptor
     * @return true if the table exists, false otherwise
     */
    default boolean exists(Table table) {
        return exists(table.namespace(), table.name());
    }

    /**
     * Drops a table from the catalog.
     *
     * @param namespace the table namespace
     * @param tableName the table name
     * @param purgeData whether to purge underlying data files
     */
    void drop(String namespace, String tableName, boolean purgeData);

    /**
     * Drops a table without purging data files.
     */
    default void drop(String namespace, String tableName) {
        drop(namespace, tableName, false);
    }

    /**
     * Drops a table.
     */
    default void drop(Table table) {
        drop(table.namespace(), table.name(), false);
    }

    /**
     * Legacy no-op drop method.
     */
    default void drop() {
    }

    /**
     * Lists all tables in a namespace.
     *
     * @param namespace the namespace to query
     * @return list of table names
     */
    List<String> listTables(String namespace);

    /**
     * Creates a namespace with optional metadata properties.
     *
     * @param namespace the namespace name
     * @param properties namespace metadata properties
     */
    void createNamespace(String namespace, Map<String, String> properties);

    /**
     * Creates a namespace with default properties.
     */
    default void createNamespace(String namespace) {
        createNamespace(namespace, Collections.emptyMap());
    }

    /**
     * Checks if a namespace exists.
     */
    boolean namespaceExists(String namespace);

    /**
     * Drops a namespace.
     */
    void dropNamespace(String namespace);

    /**
     * Lists all top-level namespaces.
     */
    List<String> listNamespaces();
}
