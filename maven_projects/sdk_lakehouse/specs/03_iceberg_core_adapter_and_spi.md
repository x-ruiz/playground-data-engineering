# Feature Spec: `lakehouse-iceberg` Core Adapter & Catalog SPI

- **Status:** Approved
- **Module / Component:** `lakehouse-iceberg` (Cloud-Agnostic Core Adapter & SPI — Java 17, Apache Iceberg)
- **Archetype:** Java Library Contract (Engine Adapter + Plugin SPI)
- **Tier:** Full Spec
- **Owner:** Xavier Ruiz
- **Depends On:** `specs/00_project_structure_and_organization.md`, `specs/01_lakehouse_core_domain_models.md`, `specs/02_lakehouse_core_managers_and_yaml.md`

---

## 1. Goal & Context

### Problem Statement
`lakehouse-core` defines engine-agnostic domain models and Template-Method managers, but platform pipelines need a concrete Apache Iceberg implementation that remains **100% cloud-agnostic**. By pairing `IcebergNamespaceManager` and `IcebergTableManager` with a `java.util.ServiceLoader`-backed `IcebergCatalogProvider` SPI (plus built-in `in_memory` and `generic` providers), pipelines can run unit tests locally with zero cloud SDKs and plug in thin AWS or GCP catalog JARs at deployment time without fat-JAR conflicts.

### Success Metrics
- `lakehouse-iceberg` compiles and passes all unit tests (`TC-001` through `TC-013`) with **zero** AWS or GCP dependencies (`iceberg-aws` and `iceberg-gcp` absent from `lakehouse-iceberg/pom.xml`).
- `IcebergCatalogFactory` discovers built-in providers (`in_memory`, `generic`) via `META-INF/services` and rejects unknown, blank, or duplicate backend IDs cleanly.
- All public classes pass strict Javadoc validation (`mvn javadoc:javadoc -pl lakehouse-iceberg -Ddoclint=all`) with 0 errors and 0 warnings.

---

## 2. Scope & Explicit Non-Goals

### In Scope
- Cloud-agnostic `lakehouse-iceberg` module (`lakehouse-core` + `iceberg-core`; removes any AWS/GCP bundles):
  - `...lakehouse.iceberg`: `IcebergNamespaceManager`, `IcebergTableManager`, and package-private `IcebergTypeMapper`.
  - `...lakehouse.iceberg.catalog`: `IcebergCatalogProvider` (SPI interface), `IcebergCatalogFactory` (`ServiceLoader` facade), `InMemoryCatalogProvider` (`"in_memory"`), and `GenericCatalogProvider` (`"generic"`).
  - `META-INF/services/...IcebergCatalogProvider` registering the two built-in providers.
- Deterministic offline unit tests using `InMemoryCatalog` and stubbed Iceberg `Catalog` failure scenarios.

### Non-Goals (Do Not Implement)
- **No cloud-specific plugins:** `lakehouse-iceberg-aws` (MinIO/AWS REST) lives in Spec 04; `lakehouse-iceberg-gcp` (BigLake REST) lives in Spec 05.
- **No catalog lifecycle registry or cached catalog instances:** Callers construct/close their own `Catalog` and pass it into managers.
- **No `dropTable`, `tableExists`, `dropNamespace`, `alterTable`, or schema evolution:** Pure idempotent `CREATE IF NOT EXISTS` no-op semantics only.
- **No multi-column partitioning, partition transforms, custom properties propagation, or Spark SQL execution:** Native Apache Iceberg Java Catalog API only.

---

## 3. Functional Requirements

### Catalog SPI & Built-In Providers (`...iceberg.catalog`)
- `REQ-01`: `IcebergCatalogProvider` shall be a `public interface` declaring `String backendType()` and `Catalog loadCatalog(CatalogModel catalogModel, Map<String, String> properties)`.
- `REQ-02`: `IcebergCatalogFactory` shall be a `public final class` exposing `static loadCatalog(String backendType, CatalogModel catalogModel, Map<String, String> properties)` and `static availableBackends()` (unmodifiable sorted set of discovered backend IDs).
- `REQ-03`: `IcebergCatalogFactory` shall discover providers via `ServiceLoader`, normalize `backendType` (trim + lowercase `Locale.ROOT`), throw `InvalidCatalogModelException` on `null` `catalogModel`, and throw `CatalogOperationException` on `null`/blank `backendType`, duplicate provider registrations, or unknown `backendType` (listing discovered backends).
- `REQ-04`: `InMemoryCatalogProvider` (`"in_memory"`) shall load `org.apache.iceberg.inmemory.InMemoryCatalog` via `CatalogUtil.buildIcebergCatalog`, defaulting `warehouse` to `"memory://warehouse"`, rejecting `null` keys/values with `CatalogOperationException`, and wrapping Iceberg errors in `CatalogOperationException` preserving cause.
- `REQ-05`: `GenericCatalogProvider` (`"generic"`) shall reject `null` `CatalogModel` (`InvalidCatalogModelException`) and `null`/empty maps or `null` entries (`CatalogOperationException`), defensively copy properties, and delegate to `CatalogUtil.buildIcebergCatalog`, wrapping failures in `CatalogOperationException` preserving cause.

### Managers & Catalog Identity Guard (`...iceberg`)
- `REQ-06`: `IcebergNamespaceManager` (`public final class extends NamespaceManager`) and `IcebergTableManager` (`public final class extends TableManager`) shall accept a single `org.apache.iceberg.catalog.Catalog` via constructor, failing fast with `CatalogOperationException` if `catalog` is `null`, `catalog.name()` is `null`/blank, or `catalog` does not implement `SupportsNamespaces`.
- `REQ-07`: Before any Iceberg call, both managers shall verify exact catalog-name equality (`catalog.name().equals(model.catalog().name())`), throwing `CatalogNotFoundException` carrying `model.catalog()` on mismatch with zero Iceberg mutations.
- `REQ-08`: `IcebergNamespaceManager` shall implement `doNamespaceExists` and idempotent `doCreateNamespace` (returning cleanly if namespace exists or if `AlreadyExistsException` is thrown).
- `REQ-09`: `IcebergTableManager.doCreateTable` shall verify the target namespace exists (throwing `NamespaceNotFoundException` carrying `model.namespace()` if absent—never auto-creating namespaces), return cleanly if the table already exists or `AlreadyExistsException` is thrown, translate `NoSuchNamespaceException` to `NamespaceNotFoundException` preserving cause, and create the table via `catalog.createTable(tableId, schema, partitionSpec)`.
- `REQ-10`: Both managers shall wrap any unexpected non-`LakehouseException` `RuntimeException` in `CatalogOperationException` preserving the original cause.

### Type, Schema & Partition Mapping
- `REQ-11`: Schema translation (`IcebergTypeMapper`, package-private) shall assign deterministic 1-based Iceberg field IDs (`1..N`), map `nullable == true/false` to `optional`/`required`, and exhaustively map all 8 `ColumnType` values (`TIMESTAMP` → `TimestampType.withZone()`, `DECIMAL` → `DecimalType.of(38, 9)`).
- `REQ-12`: Partition translation shall map unpartitioned tables to `PartitionSpec.unpartitioned()` and partitioned tables to an identity partition spec on `model.partitionColumn()`.

---

## 4. Public Java API Contract

### Public Types (`lakehouse-iceberg`)

| Type | Package & Visibility | Public API | Contract |
| :--- | :--- | :--- | :--- |
| `IcebergNamespaceManager` | `...iceberg` (`public final extends NamespaceManager`) | `public IcebergNamespaceManager(Catalog)` | Validates `Catalog` at construction; enforces catalog match, idempotent namespace creation, and exception wrapping. |
| `IcebergTableManager` | `...iceberg` (`public final extends TableManager`) | `public IcebergTableManager(Catalog)` | Validates `Catalog` at construction; enforces catalog match, strict namespace existence, schema/partition mapping, and idempotent creation. |
| `IcebergCatalogProvider` | `...iceberg.catalog` (`public interface`) | `String backendType()`<br/>`Catalog loadCatalog(CatalogModel, Map<String, String>)` | SPI contract implemented by built-in and cloud plugin JARs. |
| `IcebergCatalogFactory` | `...iceberg.catalog` (`public final class`) | `static loadCatalog(String, CatalogModel, Map<String, String>)`<br/>`static availableBackends()` | Stateless `ServiceLoader` facade routing normalized `backendType` to the registered provider. |
| `InMemoryCatalogProvider` | `...iceberg.catalog` (`public final class`) | No-arg ctor; `"in_memory"` | Loads `InMemoryCatalog` with default `warehouse=memory://warehouse`. |
| `GenericCatalogProvider` | `...iceberg.catalog` (`public final class`) | No-arg ctor; `"generic"` | Passes caller-supplied properties directly to `CatalogUtil.buildIcebergCatalog`. |

*(Package-private internals such as `IcebergTypeMapper` and test-only factory overloads remain encapsulated implementation details.)*

### Canonical `ColumnType` → Apache Iceberg `Type` Mapping

| `ColumnType` | Apache Iceberg `Type` | Notes |
| :--- | :--- | :--- |
| `STRING` / `INT` / `BIGINT` / `DOUBLE` / `BOOLEAN` / `DATE` | `StringType` / `IntegerType` / `LongType` / `DoubleType` / `BooleanType` / `DateType` | Standard Iceberg primitive types |
| `TIMESTAMP` | `Types.TimestampType.withZone()` | UTC instant (`timestamptz`) |
| `DECIMAL` | `Types.DecimalType.of(38, 9)` | Precision 38, scale 9 (`NUMERIC`-compatible) |

### Sandbox External Config Example (`backend=in_memory`)
```properties
warehouse=memory://warehouse
```

---

## 5. Invariants, Edge Cases & Failure Modes

### Key Invariants
1. **Strict Cloud Isolation:** Zero `iceberg-aws`, `iceberg-gcp`, AWS SDK, or GCP SDK dependencies in `lakehouse-iceberg/pom.xml` or source code.
2. **Stateless SPI & Factory:** Providers and factory hold zero cached catalogs or mutable state; callers own closing `AutoCloseable` catalogs.
3. **Cross-Environment Safety:** Exact `catalog.name()` check prevents running a `prod` YAML against a `dev` catalog.
4. **Concurrent Idempotency & Cause Preservation:** `AlreadyExistsException` is absorbed cleanly; every wrapped `CatalogOperationException` or TOCTOU `NamespaceNotFoundException` preserves its cause.

### Failure Mode Matrix

| Failure Condition | Expected Exception | Notes |
| :--- | :--- | :--- |
| `null`/blank/unknown `backendType`, or duplicate provider registration | `CatalogOperationException` | Unknown-backend message lists discovered backends. |
| `null` `CatalogModel` passed to factory or provider | `InvalidCatalogModelException` | Fails fast before invoking Iceberg. |
| `null`/empty/null-entry properties in `GenericCatalogProvider`, or `null` key/value in `InMemoryCatalogProvider` | `CatalogOperationException` | Descriptive diagnostic message. |
| `null` `Catalog`, blank `catalog.name()`, or non-`SupportsNamespaces` catalog | `CatalogOperationException` | Fails fast at manager construction. |
| YAML `catalog` name differs from `catalog.name()` | `CatalogNotFoundException` | Carries `model.catalog()`; zero Iceberg calls made. |
| Target namespace missing on `createTable` (pre-check or `NoSuchNamespaceException`) | `NamespaceNotFoundException` | Carries `model.namespace()`; preserves cause on race. |
| Namespace or table already exists (pre-check or `AlreadyExistsException`) | None (no-op) | Idempotent success. |
| Underlying Iceberg / I/O `RuntimeException` | `CatalogOperationException` | Preserves original Iceberg cause. |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | Built-in SPI discovery | `availableBackends()` and `loadCatalog("in_memory", CatalogModel("data_lake"), Map.of())` | Discovers `["generic", "in_memory"]`; loads initialized `InMemoryCatalog` named `"data_lake"`. |
| `TC-002` | `GenericCatalogProvider` passthrough & validation | Valid `InMemoryCatalog` props + `null` model, empty/null-entry, and invalid Iceberg props | Valid props return `Catalog`; invalid inputs throw `InvalidCatalogModelException` / `CatalogOperationException` preserving cause. |
| `TC-003` | Factory normalization & error cases | `null`, `"  "`, `"gcp_biglake"`, `" IN_MEMORY "`, and duplicate provider registrations | Trimmed case-insensitive lookup succeeds; invalid/unknown/duplicate cases throw `CatalogOperationException` listing available backends. |
| `TC-004` | Manager constructor validation | `null`, blank-named, or non-`SupportsNamespaces` catalog | Throws `CatalogOperationException`. |
| `TC-005` | Catalog name mismatch guard | `InMemoryCatalog("dev_lake")` + YAML targeting `"data_lake"` | All manager operations throw `CatalogNotFoundException("data_lake")` with zero mutations. |
| `TC-006` | `IcebergNamespaceManager` happy path & idempotency | `InMemoryCatalog("data_lake")` + `namespace.yaml` | `namespaceExists` flips `false` → `true`; repeated `createNamespace` is a clean no-op. |
| `TC-007` | Strict missing-namespace rejection | `InMemoryCatalog("data_lake")` without namespace + `table.yaml` | Throws `NamespaceNotFoundException`; namespace is not auto-created. |
| `TC-008` | Partitioned table creation happy path | Existing `market_data` namespace + `table.yaml` | Creates table with 3 columns (`DATE`, `LONG`, `DECIMAL(38, 9)`), field IDs `1..3`, required/optional flags, and identity partition on `date`. |
| `TC-009` | Unpartitioned table & default nullability | YAML omitting `partitionColumn` and column `nullable` | Creates `PartitionSpec.unpartitioned()` with optional Iceberg field. |
| `TC-010` | Table creation idempotency | Call `createTable(tableYaml)` twice | Second call is a clean no-op without modifying the existing table. |
| `TC-011` | Exhaustive `ColumnType` mapping | All 8 `ColumnType` values with mixed `nullable` flags | Exact Iceberg type mapping (`TIMESTAMP` with zone, `DECIMAL(38, 9)`), 1-based field IDs, preserved order. |
| `TC-012` | Race conditions & unexpected error wrapping | Stub `Catalog` throwing `AlreadyExistsException`, `NoSuchNamespaceException`, and generic `RuntimeException` | `AlreadyExistsException` absorbed; `NoSuchNamespaceException` → `NamespaceNotFoundException` (with cause); unexpected errors → `CatalogOperationException` (with cause). |
| `TC-013` | Cloud isolation & strict Javadoc hygiene | Audit `lakehouse-iceberg/pom.xml`, package visibility, and `mvn javadoc:javadoc -pl lakehouse-iceberg -Ddoclint=all` | Zero AWS/GCP deps; `IcebergTypeMapper` is package-private; Javadoc builds with 0 errors and 0 warnings. |
