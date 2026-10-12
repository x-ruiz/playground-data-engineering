# GEMINI.md - Project Context & Metadata for `sdk_lakehouse`

## 1. Project Overview & Vision

**Lakehouse SDK (`sdk.lakehouse`)** is a modular, high-performance Java SDK designed to manage open-source table formats—principally **Apache Iceberg**—across modern data lakehouse architectures. It provides engine-agnostic abstractions and concrete implementations for table lifecycle management (DDL, schema evolution, catalog registration, table creation, maintenance) across distributed compute engines (Apache Spark) and catalog services (Apache Iceberg REST Catalog, AWS Glue, Hive Metastore, Nessie, JDBC) with local/cloud object storage (MinIO S3, AWS S3, Google Cloud Storage).

---

## 2. Architecture & Module Structure

The project follows a multi-module Maven structure separating declarative domain contracts from engine-specific execution runtimes.

```
sdk_lakehouse/
├── pom.xml                               # Aggregator root POM (parent: parent.iceberg)
├── lakehouse-core/                       # Engine-agnostic abstractions, contracts, and interfaces
│   ├── pom.xml
│   └── src/main/java/.../lakehouse/core/
│       ├── Catalog.java                  # Catalog descriptor contract
│       ├── Table.java                    # Table metadata & schema contract
│       ├── TableManager.java             # Table lifecycle management interface (DDL, CRUD)
│       ├── LakehouseFactory.java         # Factory contract for managers and sessions
│       └── InvalidTableException.java    # Core validation exception
├── lakehouse-iceberg/                    # Iceberg-specific implementations and Spark runtime
│   ├── pom.xml
│   └── src/main/java/.../lakehouse/iceberg/
│       ├── CatalogType.java              # REST, GLUE, JDBC, HIVE, NESSIE, HADOOP
│       ├── CloudProviderType.java        # GCP, AWS
│       ├── IcebergCatalog.java           # Concrete Iceberg catalog descriptor & builder
│       ├── IcebergTable.java             # Immutable Iceberg table descriptor & builder
│       ├── IcebergNativeTableManager.java # Pure-Java Iceberg Catalog lifecycle manager (No Spark)
│       ├── SparkIcebergTableManager.java # Dynamic Spark SQL DDL table manager
│       ├── IcebergLakehouseFactory.java  # Factory producing Iceberg managers & Spark sessions
│       └── Main.java                     # CLI entrypoint for spark-submit
├── lakehouse-schema-protobuf/           # Protobuf schema converter and table spec parser
│   ├── pom.xml
│   ├── src/main/proto/
│   │   └── iceberg_options.proto         # Declarative table and field custom options
│   └── src/main/java/.../lakehouse/protobuf/
│       ├── ProtoToIcebergSchemaConverter.java # Proto descriptor to Iceberg Schema converter
│       └── ProtoTableSpecParser.java     # Batch/Streaming TableSpec parser
├── docker-compose.yaml                   # Local multi-service infrastructure
├── Makefile                              # Build, deploy, and execution automation
├── TODO.md                               # Project backlog and task tracking
└── README.md                             # Architectural overview & documentation
```

### Module Responsibilities

| Module | Scope & Responsibility | Key Dependencies |
| :--- | :--- | :--- |
| **`lakehouse-core`** | Engine-agnostic domain contracts (`Catalog`, `Table`, `TableManager`, `TableSpec`, `WorkloadMode`, `LakehouseFactory`). Pure Java contracts independent of heavy compute frameworks. | `log4j-api`, `log4j-core`, `spark-sql` (provided) |
| **`lakehouse-iceberg`** | Apache Iceberg concrete implementations: native pure-Java catalog manager (`IcebergNativeTableManager`), Spark SQL manager (`SparkIcebergTableManager`), schema parser, partition helpers, and shaded execution JAR. | `lakehouse-core`, `iceberg-core`, `iceberg-aws`, `iceberg-aws-bundle`, `spark-sql` (provided) |
| **`lakehouse-schema-protobuf`** | Protobuf schema parser, field-ID mapping, table spec generator for batch and streaming pipelines. | `lakehouse-core`, `protobuf-java`, `iceberg-core`, `iceberg-api` |

---

## 3. Technology Stack & Versions

- **Java**: OpenJDK 17 (Target 17)
- **Build Tool**: Apache Maven 3.9+
- **Apache Iceberg**: `1.7.2`
- **Apache Spark**: `3.5.1` (Scala binary version `2.12`)
- **Logging**: Log4j 2 (`2.24.1`)
- **Storage / S3 API**: MinIO (local S3-compatible object store)
- **Catalog**: Apache Iceberg REST Catalog Fixture (`apache/iceberg-rest-fixture`)
- **Docker Compose**: Spark Master/Worker (`tabulario/spark-iceberg`), REST Catalog, MinIO, MinIO Client (`mc`)

---

## 4. Local Infrastructure & Environment

The local ecosystem is orchestrated via `docker-compose.yaml`:

| Container Service | Image | Internal Port | Host Port | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `spark-iceberg` | `tabulario/spark-iceberg` | 7077, 8080, 8888 | `8080` (Web UI), `8888` (Jupyter) | Spark Master, worker, and execution container |
| `iceberg-rest` | `apache/iceberg-rest-fixture` | 8181 | `8181` | Apache Iceberg REST Catalog service |
| `minio` | `minio/minio` | 9000, 9001 | `9000` (S3 API), `9001` (Console) | S3 Object Storage (`s3://warehouse/`) |
| `mc` | `minio/mc` | N/A | N/A | MinIO CLI bootstrap: provisions `warehouse` bucket and access |

### Credentials & Endpoints
- **MinIO Access Key**: `admin`
- **MinIO Secret Key**: `password`
- **S3 Endpoint (Internal Docker)**: `http://minio:9000`
- **S3 Endpoint (Localhost)**: `http://localhost:9000`
- **REST Catalog URI (Internal Docker)**: `http://iceberg-rest:8181`
- **REST Catalog URI (Localhost)**: `http://localhost:8181`
- **Warehouse Location**: `s3://warehouse/`

---

## 5. Development Workflows & Commands

### Maven Build Commands
```bash
# Clean and compile entire reactor
mvn clean compile

# Build and package all modules (skipping tests)
mvn clean package -DskipTests

# Run tests
mvn test
```

### Deployment & Execution
```bash
# Build uber JAR, copy to spark container, and execute spark-submit
make deploy

# Launch interactive Spark SQL shell inside container
make spark-sql
```

### Docker Management
```bash
# Start all lakehouse infrastructure services in background
docker-compose up -d

# Check status of running services
docker-compose ps

# Tear down infrastructure
docker-compose down
```

---

## 6. Coding Standards & Architectural Guidelines

1. **Decoupled Architecture**:
   - Do NOT tightly couple table schema and metadata definitions to Spark SQL string concatenation.
   - Core contracts (`lakehouse-core`) must remain compute-agnostic.
   - DDL operations (create table, alter schema, drop table) should support **pure Iceberg Catalog API** (`org.apache.iceberg.catalog.Catalog`) without requiring a heavyweight Spark session, with optional Spark SQL adapters.
2. **Catalog Injection**:
   - Table managers (`TableManager`) must accept an injected `Catalog` descriptor or Iceberg catalog instance.
   - Never hardcode catalog names like `"demo"` or `"default"`.
3. **Builder Pattern & Immutability**:
   - Domain objects (`IcebergTable`, `IcebergCatalog`, `TableSpec`) must be immutable and constructed via type-safe fluent builders with null-safety and validation (`Objects.requireNonNull`).
4. **Structured Schemas & Field ID Integrity**:
   - Avoid plain-string schemas (`String schema`). Use structured representations (`org.apache.iceberg.Schema`, `Types.NestedField`) ensuring stable `field-id` assignment to guarantee deterministic schema evolution.
5. **Logging**:
   - Use SLF4J / Log4j 2 facades (`org.apache.logging.log4j.LogManager`). Avoid `System.out.println`.

---

## 7. Known Technical Debt & Immediate Gaps

| Area | Current State | Target State | Status |
| :--- | :--- | :--- | :--- |
| **Catalog Hardcoding** | Injected catalog name via constructor/factory (`IcebergCatalog`). | Injected catalog name via constructor/factory. | **Resolved** |
| **Schema Definition** | Structured `TableSpec` + `IcebergSchemaParser` supporting typed Iceberg `Schema` and PartitionSpec. | Structured schema model with Iceberg `Schema` support, partition specs, and sort orders. | **Resolved** |
| **Spark Coupling** | Introduced pure-Java `IcebergNativeTableManager` using `org.apache.iceberg.catalog.Catalog` (REST/S3). Spark is optional. | Introduce `IcebergCatalogTableManager` using pure Java `org.apache.iceberg.catalog.Catalog` (REST/Glue/JDBC), alongside `SparkIcebergTableManager`. | **Resolved** |
| **CRUD Operations** | Full lifecycle CRUD: `create`, `drop(purge)`, `exists`, `listTables`, namespace management. | Complete CRUD lifecycle interface on `TableManager`. | **Resolved** |
| **POM Dependencies** | Deduplicated POM dependencies and added JDK 17 compiler `<release>17</release>` configuration. | Deduplicate POM dependencies and clean compiler flags. | **Resolved** |

---

## 8. Active Roadmap

- [X] **Protobuf Table Spec Module (`lakehouse-schema-protobuf`)**:
  - [X] Protobuf IDL to Iceberg Schema conversion (`ProtoToIcebergSchemaConverter` for primitives, WKTs, repeated, nested).
  - [X] Custom protobuf options (`iceberg_options.proto`) for partition transforms (`identity`, `day`, `hour`, `bucket`), sort orders, and table properties.
  - [X] Dual batch & streaming table creation pipelines (`ProtoTableSpecParser`).
- [X] **General Iceberg Table Creation SDK Refactor**:
  - [X] Pure Iceberg native catalog manager (`IcebergNativeTableManager`) connecting to REST/S3 without Spark.
  - [X] Full CRUD lifecycle (`create`, `drop`, `exists`, `listTables`, namespace management).
  - [X] Dynamic DDL builder for Spark SQL execution (`SparkIcebergTableManager`).
  - [X] Multi-catalog and cloud credentials support (`IcebergCatalog`).
- [ ] **Streaming & CDC Optimizations**:
  - Iceberg v2 format table configuration (row-level deletes, equality delete IDs).
  - Target file sizes and streaming commit intervals (`commit.manifest.min-count-to-merge`).
- [ ] **Table Maintenance Utilities**:
  - Automated compaction (`rewrite_data_files`).
  - Snapshot expiration (`expire_snapshots`) and orphan file removal (`remove_orphan_files`).
