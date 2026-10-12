package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.rest.RESTCatalog;
import x.ruiz.playground.data.engineering.lakehouse.core.Catalog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IcebergCatalog implements Catalog {
    private static final String CATALOG_CONFIG_PREFIX_TEMPLATE = "spark.sql.catalog.%s";
    private static final String CATALOG_IMPL = "org.apache.iceberg.spark.SparkCatalog";

    private final String name;
    private final CatalogType type;
    private final String uri;
    private final String warehouse;
    private final String ioImpl;
    private final String s3Endpoint;
    private final String s3PathStyleAccess;
    private final String s3AccessKey;
    private final String s3SecretKey;
    private final Map<String, String> customProperties;

    private IcebergCatalog(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.uri = builder.uri;
        this.warehouse = builder.warehouse;
        this.ioImpl = builder.ioImpl;
        this.s3Endpoint = builder.s3Endpoint;
        this.s3PathStyleAccess = builder.s3PathStyleAccess;
        this.s3AccessKey = builder.s3AccessKey;
        this.s3SecretKey = builder.s3SecretKey;
        this.customProperties = Collections.unmodifiableMap(new HashMap<>(builder.customProperties));
    }

    public static Builder builder(CatalogType type) {
        return new Builder(type);
    }

    public static class Builder {
        private String name;
        private final CatalogType type;
        private String uri;
        private String warehouse = "s3://warehouse/";
        private String ioImpl = "org.apache.iceberg.aws.s3.S3FileIO";
        private String s3Endpoint = "http://minio:9000";
        private String s3PathStyleAccess = "true";
        private String s3AccessKey = System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "admin");
        private String s3SecretKey = System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "password");
        private final Map<String, String> customProperties = new HashMap<>();

        private Builder(CatalogType type) {
            this.type = Objects.requireNonNull(type, "CatalogType must not be null");
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder warehouse(String warehouse) {
            this.warehouse = warehouse;
            return this;
        }

        public Builder ioImpl(String ioImpl) {
            this.ioImpl = ioImpl;
            return this;
        }

        public Builder s3Endpoint(String s3Endpoint) {
            this.s3Endpoint = s3Endpoint;
            return this;
        }

        public Builder s3PathStyleAccess(String s3PathStyleAccess) {
            this.s3PathStyleAccess = s3PathStyleAccess;
            return this;
        }

        public Builder s3AccessKey(String accessKey) {
            this.s3AccessKey = accessKey;
            return this;
        }

        public Builder s3SecretKey(String secretKey) {
            this.s3SecretKey = secretKey;
            return this;
        }

        public Builder property(String key, String value) {
            this.customProperties.put(key, value);
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            if (properties != null) {
                this.customProperties.putAll(properties);
            }
            return this;
        }

        public IcebergCatalog build() {
            Objects.requireNonNull(this.name, "name property must not be empty or null");

            if (this.type == CatalogType.REST) {
                String message = String.format("uri property must not be empty for catalog type %s", CatalogType.REST);
                Objects.requireNonNull(this.uri, message);
            }
            return new IcebergCatalog(this);
        }
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String catalogConfigPrefix() {
        return String.format(CATALOG_CONFIG_PREFIX_TEMPLATE, this.name);
    }

    @Override
    public Map<String, String> properties() {
        Map<String, String> props = new HashMap<>(customProperties);
        if (uri != null) props.put(CatalogProperties.URI, uri);
        if (warehouse != null) props.put(CatalogProperties.WAREHOUSE_LOCATION, warehouse);
        if (ioImpl != null) props.put(CatalogProperties.FILE_IO_IMPL, ioImpl);
        if (s3Endpoint != null) props.put("s3.endpoint", s3Endpoint);
        if (s3PathStyleAccess != null) props.put("s3.path-style-access", s3PathStyleAccess);
        if (s3AccessKey != null) props.put("s3.access-key-id", s3AccessKey);
        if (s3SecretKey != null) props.put("s3.secret-access-key", s3SecretKey);
        return Collections.unmodifiableMap(props);
    }

    /**
     * Initializes and returns an instance of Apache Iceberg's native Catalog client.
     */
    public org.apache.iceberg.catalog.Catalog loadNativeCatalog() {
        Map<String, String> props = new HashMap<>(properties());

        org.apache.iceberg.catalog.Catalog catalog;
        switch (this.type) {
            case REST -> catalog = new RESTCatalog();
            case HADOOP -> {
                org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
                return new HadoopCatalog(conf, this.warehouse);
            }
            default -> {
                String catalogClassName = switch (this.type) {
                    case GLUE -> "org.apache.iceberg.aws.glue.GlueCatalog";
                    case JDBC -> "org.apache.iceberg.jdbc.JdbcCatalog";
                    case HIVE -> "org.apache.iceberg.hive.HiveCatalog";
                    case NESSIE -> "org.apache.iceberg.nessie.NessieCatalog";
                    default -> "org.apache.iceberg.rest.RESTCatalog";
                };
                return CatalogUtil.loadCatalog(catalogClassName, this.name, props, new org.apache.hadoop.conf.Configuration());
            }
        }

        catalog.initialize(this.name, props);
        return catalog;
    }

    public String catalogImpl() {
        return CATALOG_IMPL;
    }

    public CatalogType type() {
        return this.type;
    }

    public String uri() {
        return this.uri;
    }

    public String warehouse() {
        return this.warehouse;
    }

    public String ioImpl() {
        return this.ioImpl;
    }

    public String s3Endpoint() {
        return this.s3Endpoint;
    }

    public String s3PathStyleAccess() {
        return this.s3PathStyleAccess;
    }

    public String s3AccessKey() {
        return this.s3AccessKey;
    }

    public String s3SecretKey() {
        return this.s3SecretKey;
    }
}
