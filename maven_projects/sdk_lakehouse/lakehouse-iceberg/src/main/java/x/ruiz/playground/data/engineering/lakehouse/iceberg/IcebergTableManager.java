package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.spark.sql.SparkSession;
import x.ruiz.playground.data.engineering.lakehouse.core.Table;
import x.ruiz.playground.data.engineering.lakehouse.core.TableManager;

public class IcebergTableManager implements TableManager {
    private static final Logger logger = LogManager.getLogger();

    public IcebergTableManager() {}

    @Override
    @SuppressWarnings("SqlSourceToSinkFlow")
    public void create(SparkSession spark, Table table) {
        logger.info("Creating Table with namespace: {}, name: {}, schema: {}", table.namespace(), table.name(), table.schema());
        spark.sql(String.format("CREATE NAMESPACE IF NOT EXISTS demo.%s", table.namespace()));
        spark.sql(String.format("""
                CREATE TABLE IF NOT EXISTS demo.%s.%s (
                    id BIGINT,
                    name STRING,
                    ts TIMESTAMP
                )
                USING iceberg
                """, table.namespace(), table.name()));

        logger.info("Table Created");
    }

    @Override
    public void drop() {

    }
}
