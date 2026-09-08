package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import x.ruiz.playground.data.engineering.lakehouse.core.Table;

import java.util.Objects;

public class IcebergTable implements Table {
    private static final Logger logger = LogManager.getLogger();

    private final String name;
    private final String namespace;
    private final String schema;

    private IcebergTable(Builder builder){
        name = builder.name;
        namespace = builder.namespace;
        schema = builder.schema;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String namespace;
        private String schema;

        private Builder(String name) {
            this.name = name;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Table build() {
            Objects.requireNonNull(this.namespace, "namespace is required");
            Objects.requireNonNull(this.schema, "schema is required");
            return new IcebergTable(this);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public String schema() {
        return schema;
    }
}
