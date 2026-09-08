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
- [ ] Fix tablemanager hardcoding demo catalog. (inject catalog into tablemanager in factory?)

### 3. General TODO
- [ ] Create a gemini cli skill to turn java todo comments into a todo list in this markdown file