package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.Schema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import x.ruiz.playground.data.engineering.lakehouse.core.Table;
import x.ruiz.playground.data.engineering.lakehouse.core.WorkloadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class IcebergTable implements Table {
    private static final Logger logger = LogManager.getLogger();

    private final String name;
    private final String namespace;
    private final String schemaString;
    private final Schema icebergSchema;
    private final Map<String, String> properties;
    private final List<String> partitionColumns;
    private final List<String> sortColumns;
    private final String location;
    private final WorkloadMode mode;

    private IcebergTable(Builder builder) {
        this.name = builder.name;
        this.namespace = builder.namespace;
        this.schemaString = builder.schemaString;
        this.properties = Collections.unmodifiableMap(new HashMap<>(builder.properties));
        this.partitionColumns = Collections.unmodifiableList(new ArrayList<>(builder.partitionColumns));
        this.sortColumns = Collections.unmodifiableList(new ArrayList<>(builder.sortColumns));
        this.location = builder.location;
        this.mode = builder.mode;

        if (builder.icebergSchema != null) {
            this.icebergSchema = builder.icebergSchema;
        } else if (this.schemaString != null && !this.schemaString.trim().isEmpty()) {
            this.icebergSchema = IcebergSchemaParser.parse(this.schemaString);
        } else {
            this.icebergSchema = new Schema();
        }
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String namespace = "default";
        private String schemaString = "";
        private Schema icebergSchema;
        private final Map<String, String> properties = new HashMap<>();
        private final List<String> partitionColumns = new ArrayList<>();
        private final List<String> sortColumns = new ArrayList<>();
        private String location;
        private WorkloadMode mode = WorkloadMode.BATCH;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Table name is required");
        }

        public Builder namespace(String namespace) {
            this.namespace = Objects.requireNonNull(namespace, "Namespace is required");
            return this;
        }

        public Builder schema(String schemaString) {
            this.schemaString = Objects.requireNonNull(schemaString, "Schema is required");
            return this;
        }

        public Builder schema(Schema schema) {
            this.icebergSchema = Objects.requireNonNull(schema, "Iceberg Schema is required");
            return this;
        }

        public Builder property(String key, String value) {
            this.properties.put(Objects.requireNonNull(key, "Property key is required"),
                    Objects.requireNonNull(value, "Property value is required"));
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            if (properties != null) {
                this.properties.putAll(properties);
            }
            return this;
        }

        public Builder partitionBy(String column) {
            this.partitionColumns.add(Objects.requireNonNull(column, "Partition column is required"));
            return this;
        }

        public Builder partitionBy(List<String> columns) {
            if (columns != null) {
                this.partitionColumns.addAll(columns);
            }
            return this;
        }

        public Builder sortBy(String column) {
            this.sortColumns.add(Objects.requireNonNull(column, "Sort column is required"));
            return this;
        }

        public Builder sortBy(List<String> columns) {
            if (columns != null) {
                this.sortColumns.addAll(columns);
            }
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder mode(WorkloadMode mode) {
            this.mode = Objects.requireNonNull(mode, "WorkloadMode is required");
            return this;
        }

        public IcebergTable build() {
            Objects.requireNonNull(this.namespace, "namespace is required");
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
        return schemaString;
    }

    public Schema icebergSchema() {
        return icebergSchema;
    }

    @Override
    public Map<String, String> properties() {
        return properties;
    }

    @Override
    public List<String> partitionColumns() {
        return partitionColumns;
    }

    @Override
    public List<String> sortColumns() {
        return sortColumns;
    }

    @Override
    public String location() {
        return location;
    }

    public WorkloadMode mode() {
        return mode;
    }
}
