# Project TODO

### 1. Refactor Project Structure & Modules
- [ ] Create Grouping Directory: `iceberg-lakehouse/`
- [X] Split into submodules: `sdk-iceberg` and `app-iceberg`
- [ ] Set `spark-sql` scope to `provided` in `iceberg-lakehouse/pom.xml`
- [ ] Configure multi-module POM hierarchy
- [ ] Set up Gemini CLI

### 2. Implement Builder Pattern & Decouple Spark
- [X] Refactor `IcebergTable` with Builder pattern
- [ ] Refactor `IcebergTableManager` to use a better creation pattern (factory pattern?)
- [ ] Create `SparkSessionFactory` in `iceberg-app` (using `http://minio:9000`)