package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import x.ruiz.playground.data.engineering.lakehouse.core.Catalog;

import java.util.Objects;

//TODO: Generalize this to be cloud provider agnostic. Let the factory dictate cloud specific properties.
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

    private IcebergCatalog(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.uri = builder.uri;
        this.warehouse = builder.warehouse;
        this.ioImpl = builder.ioImpl;
        this.s3Endpoint = builder.s3Endpoint;
        this.s3PathStyleAccess = builder.s3PathStyleAccess;
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

        private Builder(CatalogType type) {
            this.type = type;
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
}
