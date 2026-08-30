# Project TODO

### 1. Refactor Project Structure & Modules
- [ ] Create Grouping Directory: `iceberg-lakehouse/`
- [ ] Split into submodules: `iceberg-sdk` and `iceberg-app`
- [ ] Set `spark-sql` scope to `provided` in `iceberg-sdk/pom.xml`
- [ ] Configure multi-module POM hierarchy
- [ ] Set up Gemini CLI

### 2. Implement Builder Pattern & Decouple Spark
- [ ] Create `IcebergTableConfig` with Builder pattern
- [ ] Refactor `IcebergTable` to accept `(SparkSession, IcebergTableConfig)`
- [ ] Create `SparkSessionFactory` in `iceberg-app` (using `http://minio:9000`)