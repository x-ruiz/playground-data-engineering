package x.ruiz.playground.data.engineering.lakehouse.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TableSpec implements Table {
    private final String name;
    private final String namespace;
    private final String schema;
    private final Map<String, String> properties;
    private final List<String> partitionColumns;
    private final List<String> sortColumns;
    private final String location;
    private final WorkloadMode mode;

    private TableSpec(Builder builder) {
        this.name = builder.name;
        this.namespace = builder.namespace;
        this.schema = builder.schema;
        this.properties = Collections.unmodifiableMap(new HashMap<>(builder.properties));
        this.partitionColumns = Collections.unmodifiableList(new ArrayList<>(builder.partitionColumns));
        this.sortColumns = Collections.unmodifiableList(new ArrayList<>(builder.sortColumns));
        this.location = builder.location;
        this.mode = builder.mode;
    }

    public static Builder builder(String name) {
        return new Builder(name);
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

    public static class Builder {
        private final String name;
        private String namespace = "default";
        private String schema = "";
        private final Map<String, String> properties = new HashMap<>();
        private final List<String> partitionColumns = new ArrayList<>();
        private final List<String> sortColumns = new ArrayList<>();
        private String location;
        private WorkloadMode mode = WorkloadMode.BATCH;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Table name must not be null");
        }

        public Builder namespace(String namespace) {
            this.namespace = Objects.requireNonNull(namespace, "Namespace must not be null");
            return this;
        }

        public Builder schema(String schema) {
            this.schema = Objects.requireNonNull(schema, "Schema must not be null");
            return this;
        }

        public Builder property(String key, String value) {
            this.properties.put(Objects.requireNonNull(key, "Property key must not be null"),
                    Objects.requireNonNull(value, "Property value must not be null"));
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            if (properties != null) {
                this.properties.putAll(properties);
            }
            return this;
        }

        public Builder partitionBy(String column) {
            this.partitionColumns.add(Objects.requireNonNull(column, "Partition column must not be null"));
            return this;
        }

        public Builder partitionBy(List<String> columns) {
            if (columns != null) {
                this.partitionColumns.addAll(columns);
            }
            return this;
        }

        public Builder sortBy(String column) {
            this.sortColumns.add(Objects.requireNonNull(column, "Sort column must not be null"));
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
            this.mode = Objects.requireNonNull(mode, "WorkloadMode must not be null");
            return this;
        }

        public TableSpec build() {
            return new TableSpec(this);
        }
    }
}
