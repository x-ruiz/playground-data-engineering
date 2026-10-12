# Project TODO

### 1. Refactor Project Structure & Modules
- [X] Create Grouping Directory: `iceberg-lakehouse/`
- [X] Split into submodules: `sdk-iceberg` and `app-iceberg`
- [X] Set `spark-sql` scope to `provided` in `iceberg-lakehouse/pom.xml`
- [X] Configure multi-module POM hierarchy
- [X] Set up Gemini CLI

### 2. Implement Builder Pattern & Decouple Spark
- [X] Refactor `IcebergTable` with Builder pattern
- [X] Create `IcebergTableManager` interface and implementations
- [X] Create `IcebergLakehouseFactory` in `lakehouse-iceberg` (using `http://minio:9000`)
- [X] Fix tablemanager hardcoding demo catalog (inject catalog into tablemanager in factory)
- [X] Introduce pure-Java `IcebergNativeTableManager` (using `org.apache.iceberg.catalog.Catalog`, eliminating mandatory Spark dependency for DDL)
- [X] Deduplicate `spark-sql` dependency in `lakehouse-core/pom.xml`

### 3. General Iceberg Table Creation SDK
- [X] Redesign `TableManager` interface for full lifecycle CRUD (`createTable`, `createIfNotExists`, `createOrReplace`, `dropTable`, `tableExists`, `loadTable`, `alterTable`, `listTables`, namespace management)
- [X] Define comprehensive `TableSpec` domain model supporting typed `Schema`, `PartitionSpec`, `SortOrder`, and table properties
- [X] Implement `SparkIcebergTableManager` with dynamic Spark SQL DDL generator
- [X] Generalize `IcebergCatalog` to support REST, Glue, Nessie, Hive, JDBC, and Hadoop catalogs with cloud-agnostic configuration

### 4. Protobuf as Iceberg Table Spec (Batch & Streaming)
- [X] Create `lakehouse-schema-protobuf` module
- [X] Implement `ProtoToIcebergSchemaConverter` (mapping primitives, WKTs, repeated fields, maps, nested structs)
- [X] Establish deterministic Field-ID mapping strategy aligning Protobuf tags with Iceberg field IDs
- [X] Define custom Protobuf options (`iceberg_options.proto`) for partition transforms (`identity`, `day`, `hour`, `bucket`), sort orders, and table properties
- [X] Support BATCH table creation profile (target file size 256-512MB, coarse partitions, hash distribution)
- [X] Support STREAMING table creation profile (Iceberg v2 format, equality delete keys for CDC/upsert, fanout distribution, aggressive snapshot expiry & manifest compaction)

### 5. Verification & Testing
- [X] End-to-end integration tests using local Docker infrastructure (`iceberg-rest` at port 8181 and MinIO at port 9000)
- [X] Test batch table creation and Spark SQL query verification
- [X] Test streaming table creation and on-the-fly schema evolution

### 6. General Backlog
- [ ] Create a gemini cli skill to turn java todo comments into a todo list in this markdown file