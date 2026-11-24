# Feature Spec: `lakehouse-core` Contracts, Composable Specs & YAML Parsers

- **Status:** Approved
- **Module:** `lakehouse-core` (Pure Java 17 — Engine-Agnostic)
- **Archetype:** Java Library Contract
- **Tier:** Mini-Spec
- **Owner:** Xavier Ruiz

---

## 1. Goal & Context

### Problem Statement
`lakehouse-core` needs a strict, self-validating, engine-agnostic contract layer before building engine-specific adapters (such as `lakehouse-iceberg`). Repeating raw `catalog` and `namespace` strings across specs and service calls invites silent typos and duplicates validation. Instead, specs should form an **acyclic, top-down immutable object chain** (`CatalogModel` → `NamespaceModel` → `TableModel` → `ColumnModel`) so that:
1. Callers reference a platform-provisioned catalog via an immutable `CatalogModel` created once, pass it into `NamespaceModel`, and pass `NamespaceModel` into `TableModel`—eliminating repeated raw identifier strings.
2. Declarative YAML specifications for **namespaces** and **tables** (GitOps pattern; never catalogs, which are provisioned externally by the platform) are parsed internally by `lakehouse-core` into the exact same validated spec hierarchy fail-fast before any engine hook executes—without exposing YAML parsing APIs to calling libraries.
3. Downstream engine modules (`lakehouse-iceberg`, future Delta/BigLake modules) extend uniform `abstract` Template-Method manager base classes (`TableManager`, `NamespaceManager`) that enforce the parse-validate-create pipeline and delegate only engine-specific persistence to `protected abstract` hooks—requiring catalogs to already exist and never auto-creating missing catalogs or namespaces.

### Success Metrics
- `lakehouse-core` compiles and passes all unit tests (`mvn test -pl lakehouse-core`) with **zero** Iceberg or Spark dependencies.
- Every acceptance case (`TC-001` through `TC-019`) is covered by deterministic JUnit 5 + AssertJ tests.
- All public contracts are immutable, strictly acyclic, thread-safe, free of serialization annotations (`@JsonProperty`, etc.) on domain models, and keep YAML parsing strictly package-private inside `...core.manager`.

---

## 2. Scope & Explicit Non-Goals

### In Scope
- Engine-agnostic composable domain models and canonical type enum in `lakehouse-core`:
  - `LakehouseModel` (`sealed interface`), `CatalogModel`, `NamespaceModel`, `TableModel`, `ColumnModel`, `ColumnType`
- Engine-agnostic `abstract` Template-Method manager base classes in `...core.manager`:
  - `TableManager` (`public final createTable(Path)` → `protected abstract doCreateTable(TableModel)`)
  - `NamespaceManager` (`public final createNamespace(Path)` → `protected abstract doCreateNamespace(NamespaceModel)`, `public final namespaceExists(Path)` & `public final namespaceExists(NamespaceModel)` → `protected abstract doNamespaceExists(NamespaceModel)`)
- Package-private stateless YAML file parser + test fixtures (internal to `...core.manager`, hidden from calling libraries):
  - Package-private `FileModelManager` interface (`parseTable(Path)`, `parseNamespace(Path)`, `readFile(Path, Class<D>, String)`)
  - Package-private `YamlFileModelManager` (`final class` implementing `FileModelManager`) overriding `parseTable`, `parseNamespace`, and `readFile`
- Custom unchecked exception hierarchy rooted at `LakehouseException`.
- Cleanup of `lakehouse-core`:
  - Replace `Column` / `ColumnDefinition` with `ColumnModel`.
  - Remove unused `spark-sql_2.12` dependency from `lakehouse-core/pom.xml`.
  - Update `lakehouse-iceberg/src/.../Main.java` only as needed to keep the multi-module Maven build compiling.

### Non-Goals (Do Not Implement)
- **No public YAML parser API or public `createTable(TableModel)` / `createNamespace(NamespaceModel)` entry points:** Calling libraries interact with `TableManager` and `NamespaceManager` via format-agnostic `Path` methods (plus `namespaceExists(NamespaceModel)`); YAML parsers remain strictly package-private inside `...core.manager` and raw `*Model` creation hooks stay `protected abstract`.
- **No `CatalogManager`, no catalog YAML parser, and no catalog/namespace properties:** Catalogs must be provisioned externally by the platform/infrastructure—never via declarative YAML and never auto-created by `lakehouse-core` services. Catalog connection configs and engine-specific namespace properties belong in engine modules (or a future overloaded API), keeping `CatalogModel` and `NamespaceModel` pure identity contracts.
- **No `lakehouse-iceberg` implementation:** Do NOT implement `IcebergTableManager`, `IcebergNamespaceManager`, `IcebergTypeMapper`, or catalog factories in this spec.
- **No engine catalog/connection types in `lakehouse-core`:** `TableManager` and `NamespaceManager` must not reference Iceberg `Catalog` or any engine-specific client.
- **No Protobuf/gRPC parsing implementation:** Keep `ProtoFileModelManager` untouched for a future spec.
- **No `dropTable`, `tableExists`, `dropNamespace`, or `alterTable`:** Only the methods explicitly listed in Section 4.
- **No schema drift detection or schema evolution:** Idempotent `createTable` on an existing table follows pure `CREATE TABLE IF NOT EXISTS` semantics (no-op).
- **No parameterized or nested column types yet:** Types like `DECIMAL(10, 2)`, `ARRAY<T>`, `MAP<K, V>`, or `STRUCT<...>` are deferred to a future spec.
- **No partition transforms:** Only single identity partition column (`partitionColumn`); transforms like `day(date)` or multi-column partitioning are deferred.

---

## 3. Functional Requirements

### Composable Domain Models & Validation
- `REQ-01`: The library shall define a `sealed interface LakehouseModel` (`permits CatalogModel, NamespaceModel, TableModel`) declaring `name()` and `fullyQualifiedName()`, and housing the shared identifier validation helper (`validateIdentifier(...)`).
- `REQ-02`: `CatalogModel(name)` shall represent a validated, immutable catalog reference (name only; catalog connection/infrastructure properties belong in engine modules) and return `name` from `fullyQualifiedName()`.
- `REQ-03`: `NamespaceModel(catalog, name)` shall compose a non-null `CatalogModel`, validate `name`, and return `"<catalog>.<namespace>"` from `fullyQualifiedName()`.
- `REQ-04`: `TableModel(namespace, name, columns, partitionColumn)` shall compose a non-null `NamespaceModel`, validate `name`, expose convenience `catalog()` delegating to `namespace.catalog()`, and return `"<catalog>.<namespace>.<tableName>"` from `fullyQualifiedName()`.
- `REQ-05`: All identifier segments (`CatalogModel.name`, `NamespaceModel.name`, `TableModel.name`, `ColumnModel.name`) shall be non-null, non-blank, and match `^[a-zA-Z_][a-zA-Z0-9_]*$` (disallowing dots, whitespace, and special characters).
- `REQ-06`: The library shall define a closed canonical `ColumnType` enum (`STRING`, `INT`, `BIGINT`, `DOUBLE`, `BOOLEAN`, `DATE`, `TIMESTAMP`, `DECIMAL`) with case-insensitive, whitespace-trimmed parsing via `ColumnType.fromString(String)`.
- `REQ-07`: `ColumnModel(name, type, nullable)` shall be a standalone, immutable leaf record (not implementing `LakehouseModel` and holding no back-reference to `TableModel`) validating `name` via `LakehouseModel.validateIdentifier(...)` and non-null `type`.
- `REQ-08`: `TableModel` shall enforce at least one column, reject `null` column entries, reject duplicate column names (case-insensitively), and defensively copy its `columns` list so callers cannot mutate internal state.
- `REQ-09`: `TableModel.partitionColumn` shall be optional (`null` represents an unpartitioned table). When non-null, it shall be non-blank and match the `name` of an existing column in `TableModel.columns` (case-insensitively).

### Manager Template Contracts
- `REQ-10`: `TableManager` shall be a `public abstract class` exposing `public final void createTable(Path sourcePath)` which parses `sourcePath` via its internal `FileModelManager` (defaulting to `YamlFileModelManager.INSTANCE`), validates the parsed `TableModel` is non-null, and delegates to `protected abstract void doCreateTable(TableModel spec)`. Subclass implementations of `doCreateTable` shall be idempotent (no-op if the table already exists), and `doCreateTable` shall never be invoked if parsing or spec validation fails.
- `REQ-11`: `TableManager.doCreateTable(TableModel)` implementations shall fail with `CatalogNotFoundException` (carrying `spec.catalog()`) if the target catalog does not exist, and with `NamespaceNotFoundException` (carrying `spec.namespace()`) if the target catalog exists but the target namespace does not exist; implementations shall never auto-create missing catalogs or namespaces.
- `REQ-12`: `NamespaceManager` shall be a `public abstract class` exposing `public final void createNamespace(Path sourcePath)` which parses `sourcePath` via its internal `FileModelManager` (defaulting to `YamlFileModelManager.INSTANCE`), validates the parsed `NamespaceModel` is non-null, and delegates to `protected abstract void doCreateNamespace(NamespaceModel spec)`. Subclass implementations of `doCreateNamespace` shall fail with `CatalogNotFoundException` (carrying `spec.catalog()`) if the target catalog does not exist (never auto-creating missing catalogs) and be idempotent (no-op if the namespace already exists); `doCreateNamespace` shall never be invoked if parsing or validation fails.
- `REQ-13`: `NamespaceManager` shall expose `public final boolean namespaceExists(Path sourcePath)` (parsing `sourcePath` into a `NamespaceModel` and delegating to `namespaceExists(NamespaceModel)`) and `public final boolean namespaceExists(NamespaceModel spec)` (rejecting `null` `spec` with `InvalidNamespaceModelException` and delegating to `protected abstract boolean doNamespaceExists(NamespaceModel spec)`). Subclass implementations of `doNamespaceExists` shall fail with `CatalogNotFoundException` (carrying `spec.catalog()`) if the target catalog does not exist and return whether the target namespace currently exists; `doNamespaceExists` shall never be invoked if parsing or null validation fails.

### Internal YAML File Parsers (Package-Private)
- `REQ-14`: `YamlFileModelManager.parseTable(Path)` shall parse a YAML file at a given `java.nio.file.Path` into a validated `TableModel` (composing `CatalogModel` → `NamespaceModel` → `TableModel` → `List<ColumnModel>`), supporting keys `catalog`, `namespace`, `tableName`, `columns` (`name`, `type`, `nullable`), and optional `partitionColumn`.
- `REQ-15`: When `nullable` is omitted for a column in YAML, `YamlFileModelManager.parseTable(Path)` shall default `nullable` to `true`.
- `REQ-16`: `YamlFileModelManager.parseNamespace(Path)` shall parse a YAML file at a given `java.nio.file.Path` into a validated `NamespaceModel` (composing `CatalogModel` → `NamespaceModel`), supporting keys `catalog` and `namespace`.
- `REQ-17`: `FileModelManager` (declaring `parseTable(Path)`, `parseNamespace(Path)`, and `readFile(Path, Class<D>, String)`) and `YamlFileModelManager` (`final class` implementing `FileModelManager` and overriding all three methods) shall be **package-private** inside `...lakehouse.core.manager` (never `public`, and never exposed in any `public` or `protected` manager signature). `YamlFileModelManager` shall reject unknown YAML fields (fail-fast on typos like `partitonColumn`), keep Jackson annotations out of domain records (via internal DTOs), and remain stateless and thread-safe. Both managers shall expose a `protected` no-arg constructor (wiring `YamlFileModelManager.INSTANCE`) and a package-private constructor accepting `FileModelManager` (failing fast on `null` parser) for internal testability and future format routing.

### Error Handling
- `REQ-18`: All library domain and parsing failures shall throw unchecked exceptions extending `LakehouseException` rather than raw `RuntimeException` or error-status DTOs.

---

## 4. Public Java API Contract

### Package Layout
- `x.ruiz.playground.data.engineering.lakehouse.core.model` — Composable immutable spec models and canonical `ColumnType`.
- `x.ruiz.playground.data.engineering.lakehouse.core.manager` — Engine-agnostic `abstract` Template-Method manager base classes (`TableManager`, `NamespaceManager`) plus **package-private** internal parsers (`FileModelManager`, `YamlFileModelManager`).
- `x.ruiz.playground.data.engineering.lakehouse.core.exception` — Custom exception hierarchy.

### Composable Hierarchy Overview
`CatalogModel` → composed by `NamespaceModel` → composed by `TableModel` → owns `List<ColumnModel>` (strictly one-directional, zero circular references).

### Domain Models (`...lakehouse.core.model`)

| Type | Kind | Fields / Values | Required Methods & Behavior |
| :--- | :--- | :--- | :--- |
| `LakehouseModel` | `sealed interface` (`permits CatalogModel, NamespaceModel, TableModel`) | — | Declares `name()` (`String`) and `fullyQualifiedName()` (`String`). Houses static `validateIdentifier(fieldName, value, exceptionFactory)` enforcing `^[a-zA-Z_][a-zA-Z0-9_]*$`. |
| `CatalogModel` | `record` (`implements LakehouseModel`) | `name` (`String`) | Compact constructor validates `name` via `LakehouseModel.validateIdentifier` (throws `InvalidCatalogModelException`). Exposes `fullyQualifiedName()` returning `name`. |
| `NamespaceModel` | `record` (`implements LakehouseModel`) | `catalog` (`CatalogModel`), `name` (`String`) | Compact constructor enforces non-null `catalog` and validates `name` via `LakehouseModel.validateIdentifier` (throws `InvalidNamespaceModelException`). Exposes `fullyQualifiedName()` returning `"<catalog>.<namespace>"`. |
| `TableModel` | `record` (`implements LakehouseModel`) | `namespace` (`NamespaceModel`), `name` (`String`), `columns` (`List<ColumnModel>`), `partitionColumn` (`String`, nullable) | Compact constructor enforces non-null `namespace`, validates `name` via `LakehouseModel.validateIdentifier` (throws `InvalidTableModelException`), enforces non-empty `columns` with no null or case-insensitively duplicate column names, defensive `List.copyOf`, and `partitionColumn` membership when non-null. Exposes `catalog()` (delegates to `namespace.catalog()`), `fullyQualifiedName()` (`"<catalog>.<namespace>.<tableName>"`), `isPartitioned()`, and `partitionColumnOptional()`. |
| `ColumnType` | `enum` | `STRING`, `INT`, `BIGINT`, `DOUBLE`, `BOOLEAN`, `DATE`, `TIMESTAMP`, `DECIMAL` | Static factory `fromString(String)` parses case-insensitively after trimming whitespace; throws `InvalidTableModelException` on null, blank, or unknown values. |
| `ColumnModel` | `record` *(leaf — not a `LakehouseModel`)* | `name` (`String`), `type` (`ColumnType`), `nullable` (`boolean`) | Compact constructor validates `name` via `LakehouseModel.validateIdentifier` and non-null `type` (throws `InvalidTableModelException`). Holds no back-pointer to `TableModel`. |

### Manager Template Base Classes (`...lakehouse.core.manager`)

| Type | Kind & Constructors | Operations | Contract |
| :--- | :--- | :--- | :--- |
| `TableManager` | `public abstract class`<br/>`protected TableManager()`<br/>*(package-private)* `TableManager(FileModelManager)` | `public final void createTable(Path)`<br/>`protected abstract void doCreateTable(TableModel)` | `createTable(Path)` parses via internal `FileModelManager.parseTable(Path)` (default `YamlFileModelManager.INSTANCE`), guards against `null` spec (`InvalidTableModelException`), and invokes `doCreateTable(spec)`. `doCreateTable` fails with `CatalogNotFoundException` if `spec.catalog()` does not exist, fails with `NamespaceNotFoundException` if `spec.namespace()` does not exist, and is an idempotent no-op if the table already exists. |
| `NamespaceManager` | `public abstract class`<br/>`protected NamespaceManager()`<br/>*(package-private)* `NamespaceManager(FileModelManager)` | `public final void createNamespace(Path)`<br/>`public final boolean namespaceExists(Path)`<br/>`public final boolean namespaceExists(NamespaceModel)`<br/>`protected abstract void doCreateNamespace(NamespaceModel)`<br/>`protected abstract boolean doNamespaceExists(NamespaceModel)` | `Path` methods parse via internal `FileModelManager.parseNamespace(Path)` (default `YamlFileModelManager.INSTANCE`). `namespaceExists(NamespaceModel)` rejects `null` with `InvalidNamespaceModelException` before invoking `doNamespaceExists(spec)`. Both `do*` hooks fail with `CatalogNotFoundException` if `spec.catalog()` does not exist; `doCreateNamespace` is an idempotent no-op if namespace already exists. |

### Internal Parser Types (`...lakehouse.core.manager` — Package-Private Only)

| Type | Kind & Visibility | Operations | Contract |
| :--- | :--- | :--- | :--- |
| `FileModelManager` | *(package-private)* `interface` | `parseTable(Path)` → `TableModel`<br/>`parseNamespace(Path)` → `NamespaceModel`<br/>`<D> D readFile(Path, Class<D>, String)` | Internal file-spec parser contract invisible outside `...core.manager`, enabling format-specific implementations (YAML today, `.proto` / `.textproto` in v2) behind the same `Path`-based manager methods. |
| `YamlFileModelManager` | *(package-private)* `final class` (`implements FileModelManager`) | `INSTANCE` singleton + package-private no-arg ctor; overrides `parseTable(Path)`, `parseNamespace(Path)`, and `readFile(Path, Class<D>, String)` | Stateless, thread-safe YAML parser using a shared strict `YAMLMapper` and private DTOs (no Jackson annotations on domain records); builds `CatalogModel` → `NamespaceModel` (→ `TableModel`); rejects unknown YAML fields; defaults omitted column `nullable` to `true`. *(There is intentionally no `parseCatalog` method; catalogs are provisioned by the platform.)* |

### Template Execution Flow

```text
Caller (lakehouse-iceberg / application)
  │
  ├─► TableManager.createTable(Path)                    [public final]
  │     ├─► FileModelManager.parseTable(Path)             [package-private → YamlFileModelManager]
  │     │     ├─► readFile(Path, YamlTableDto.class, "table")
  │     │     └─► validates CatalogModel → NamespaceModel → TableModel
  │     └─► doCreateTable(TableModel)                    [protected abstract — engine subclass]
  │
  ├─► NamespaceManager.createNamespace(Path)            [public final]
  │     ├─► FileModelManager.parseNamespace(Path)         [package-private → YamlFileModelManager]
  │     │     ├─► readFile(Path, YamlNamespaceDto.class, "namespace")
  │     │     └─► validates CatalogModel → NamespaceModel
  │     └─► doCreateNamespace(NamespaceModel)            [protected abstract — engine subclass]
  │
  └─► NamespaceManager.namespaceExists(Path | NamespaceModel)  [public final]
        ├─► (if Path) FileModelManager.parseNamespace(Path)
        ├─► non-null guard → InvalidNamespaceModelException
        └─► doNamespaceExists(NamespaceModel)            [protected abstract — engine subclass]
```

### Exception Hierarchy (`...lakehouse.core.exception`)

| Exception | Parent | Constructors / Extra Members | When Thrown |
| :--- | :--- | :--- | :--- |
| `LakehouseException` | `RuntimeException` (`abstract`) | `(String message)`, `(String message, Throwable cause)` | Base unchecked exception for all `lakehouse-core` domain and parsing errors. |
| `InvalidCatalogModelException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Invalid `CatalogModel` name or `null` `CatalogModel` passed to `CatalogNotFoundException`. |
| `InvalidNamespaceModelException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Invalid `NamespaceModel` (null `CatalogModel`, invalid name), `null` `NamespaceModel` passed to `NamespaceManager.namespaceExists(NamespaceModel)` or `NamespaceNotFoundException`, or `null` spec returned by an internal `FileModelManager`. |
| `InvalidTableModelException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Invalid `TableModel` (null `NamespaceModel`, invalid name, bad columns/partition), invalid `ColumnModel`, invalid `ColumnType`, or `null` spec returned by an internal `FileModelManager`. |
| `ModelParseException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Missing/unreadable file, `null` `Path`, empty file, malformed YAML syntax, or unknown YAML property. |
| `CatalogNotFoundException` | `LakehouseException` | `(CatalogModel catalog)`, `(CatalogModel catalog, Throwable cause)`; exposes `getCatalogModel()` and `getFullyQualifiedName()` | Target catalog has not been provisioned by the platform during `NamespaceManager.createNamespace(...)`, `NamespaceManager.namespaceExists(...)`, or `TableManager.createTable(...)`. |
| `NamespaceNotFoundException` | `LakehouseException` | `(NamespaceModel namespace)`, `(NamespaceModel namespace, Throwable cause)`; exposes `getNamespaceModel()` and `getFullyQualifiedName()` | Target namespace does not exist (within an existing catalog) during `TableManager.createTable(...)`. |
| `CatalogOperationException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Unexpected catalog, network, or storage failure in a `TableManager` / `NamespaceManager` subclass. |

### Declarative YAML Formats

**Table YAML (`table.yaml` — preserves existing keys):**
```yaml
catalog: data_lake
namespace: market_data
tableName: transactions
columns:
  - name: date
    type: DATE
    nullable: false
  - name: id
    type: BIGINT
    nullable: false
  - name: amount
    type: DECIMAL
    nullable: true
partitionColumn: date
```

**Namespace YAML (`namespace.yaml` — new):**
```yaml
catalog: data_lake
namespace: market_data
```

---

## 5. Invariants, Edge Cases & Failure Modes

### Key Invariants
1. **Strict Immutability & Acyclicity:** All spec records are deeply immutable and strictly one-directional (`CatalogModel` ← `NamespaceModel` ← `TableModel` → `ColumnModel`). Mutating any caller `List` after constructing `TableModel` must not affect the spec, and attempting to mutate `spec.columns()` must throw `UnsupportedOperationException`.
2. **Engine Independence:** Zero imports from `org.apache.iceberg.*` or `org.apache.spark.*` anywhere in `lakehouse-core`.
3. **Thread Safety:** `YamlFileModelManager` holds no per-call mutable state (uses a single `private static final YAMLMapper` configured once) and is safe to invoke concurrently across threads.
4. **Strict Parser Encapsulation & Unbypassable Template Flow:** `FileModelManager` and `YamlFileModelManager` are package-private in `...core.manager`, invisible to calling libraries on both classpath and module-path runtimes. Because `createTable(Path)`, `createNamespace(Path)`, `namespaceExists(Path)`, and `namespaceExists(NamespaceModel)` are `final`, no subclass can skip parsing or null checks, and `doCreateTable` / `doCreateNamespace` / `doNamespaceExists` are never called when parsing or validation fails.
5. **No Silent Catalog or Namespace Creation:** Catalogs must be pre-provisioned by the platform (never via declarative YAML). `NamespaceManager` and `TableManager` subclasses must reject missing catalogs with `CatalogNotFoundException` and never auto-create them; `TableManager.doCreateTable` must reject missing namespaces with `NamespaceNotFoundException`.
6. **No Exception Swallowing:** Every wrapped exception must preserve the original `Throwable cause`.

### Failure Mode Matrix

| Failure Condition | Expected Exception | Notes |
| :--- | :--- | :--- |
| `sourcePath` is `null` (via `createTable(Path)`, `createNamespace(Path)`, `namespaceExists(Path)`, or `YamlFileModelManager`) | `ModelParseException` | Fail fast; subclass hooks are never invoked |
| File does not exist or cannot be read (`IOException`) | `ModelParseException` | Preserves `IOException` cause; subclass hooks not invoked |
| Malformed YAML syntax or unknown YAML property | `ModelParseException` | Preserves Jackson exception cause; subclass hooks not invoked |
| Empty YAML file (deserializes to `null`) | `ModelParseException` | Rejects empty/comment-only file; subclass hooks not invoked |
| Missing/blank/invalid `CatalogModel.name` | `InvalidCatalogModelException` | Applies to direct construction and YAML parsing; subclass hooks not invoked |
| `NamespaceModel` given `null` `CatalogModel` or invalid `name` | `InvalidNamespaceModelException` | Applies to direct construction and YAML parsing; subclass hooks not invoked |
| `TableModel` given `null` `NamespaceModel`, invalid `name`, empty/null/duplicate `columns`, or invalid `partitionColumn` | `InvalidTableModelException` | Duplicate column check is case-insensitive; `null` `partitionColumn` is valid (unpartitioned), blank string is invalid; subclass hooks not invoked |
| Invalid `ColumnModel.name`, `null` `ColumnType`, or unknown/blank `ColumnType.fromString(...)` | `InvalidTableModelException` | Message includes offending type value |
| `NamespaceManager.namespaceExists((NamespaceModel) null)` | `InvalidNamespaceModelException` | Enforced once by base class; `doNamespaceExists` not invoked |
| Package-private manager constructor given `null` `FileModelManager` | `IllegalArgumentException` | Fail fast at construction |
| Custom internal `FileModelManager` returns `null` spec | `InvalidTableModelException` / `InvalidNamespaceModelException` | Enforced by base class; `do*` hooks not invoked |
| Target catalog missing (not provisioned by platform) during `createNamespace`, `namespaceExists`, or `createTable` | `CatalogNotFoundException` | Exposes `getCatalogModel()` and `getFullyQualifiedName()`; never auto-creates catalog |
| Target namespace missing (within an existing catalog) during `createTable` | `NamespaceNotFoundException` | Exposes `getNamespaceModel()` and `getFullyQualifiedName()` |
| Underlying catalog / network / storage error | `CatalogOperationException` | Preserves underlying cause |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | Valid table YAML (happy path) | Existing `src/test/resources/table.yaml` | `YamlFileModelManager.parseTable(path)` (via `FileModelManager`) returns `TableModel` with `fullyQualifiedName() == "data_lake.market_data.transactions"`, `namespace().fullyQualifiedName() == "data_lake.market_data"`, `catalog().fullyQualifiedName() == "data_lake"`, 3 typed `ColumnModel`s (`DATE`, `BIGINT`, `DECIMAL`), and `partitionColumn == "date"` (`isPartitioned() == true`). |
| `TC-002` | Unpartitioned table & default nullability | Valid YAML omitting `partitionColumn` and omitting `nullable` on a column | Returns `TableModel` with `isPartitioned() == false`, `partitionColumnOptional().isEmpty()`, and omitted `nullable` defaulted to `true`. |
| `TC-003` | Case-insensitive `ColumnType` parsing | YAML column `type: " date "` or `ColumnType.fromString("bigint")` | Parses cleanly to `ColumnType.DATE` / `ColumnType.BIGINT`. |
| `TC-004` | Invalid `ColumnType` | YAML column `type: "VARCHR"` or `null` | Throws `InvalidTableModelException` mentioning invalid type. |
| `TC-005` | Invalid identifier format at each spec level | `name` is `null`, blank, or contains `.` / spaces (e.g. `"market.data"`) on `CatalogModel`, `NamespaceModel`, `TableModel`, or `ColumnModel` | Throws `InvalidCatalogModelException`, `InvalidNamespaceModelException`, or `InvalidTableModelException` respectively. |
| `TC-006` | Null parent composition | `new NamespaceModel(null, "ns")` or `new TableModel(null, "tbl", cols, null)` | Throws `InvalidNamespaceModelException` (null `CatalogModel`) / `InvalidTableModelException` (null `NamespaceModel`)—never raw `NullPointerException`. |
| `TC-007` | Empty or duplicate columns | `TableModel` with empty column list, `null` entry, or columns `["id", "ID"]` | Throws `InvalidTableModelException`. |
| `TC-008` | Invalid `partitionColumn` | `partitionColumn = "non_existent"` or `partitionColumn = "  "` | Throws `InvalidTableModelException`. |
| `TC-009` | Unknown YAML key in table YAML | YAML containing typo `partitonColumn: date` | `YamlFileModelManager.parseTable(path)` throws `ModelParseException` (with Jackson cause). |
| `TC-010` | Missing / unreadable / malformed YAML file | Non-existent `Path`, `null` `Path`, empty file, or invalid YAML syntax | Both `YamlFileModelManager.parseTable` and `YamlFileModelManager.parseNamespace` throw `ModelParseException` with underlying cause when applicable. |
| `TC-011` | Valid namespace YAML (happy path) | Valid `src/test/resources/namespace.yaml` with `catalog` and `namespace` | `YamlFileModelManager.parseNamespace(path)` (via `FileModelManager`) returns `NamespaceModel` with `catalog().fullyQualifiedName() == "data_lake"` and `fullyQualifiedName() == "data_lake.market_data"`. |
| `TC-012` | Invalid namespace YAML | Missing `catalog` or `namespace`, invalid identifier, or unknown YAML key | Throws `InvalidCatalogModelException` / `InvalidNamespaceModelException` (for semantic errors) or `ModelParseException` (for unknown key / syntax error). |
| `TC-013` | Defensive immutability | Mutate source `List` after constructing `TableModel`, and attempt `.add()` on `spec.columns()` | Spec contents remain unchanged; direct mutation throws `UnsupportedOperationException`. |
| `TC-014` | `CatalogNotFoundException`, `NamespaceNotFoundException` & exception hierarchy | Construct `CatalogNotFoundException` with `new CatalogModel("missing_catalog")` and `NamespaceNotFoundException` with `new NamespaceModel(new CatalogModel("data_lake"), "missing_ns")` | Both extend `LakehouseException`, expose their exact specs (`getCatalogModel()` / `getNamespaceModel()`), `getFullyQualifiedName()` (`"missing_catalog"` / `"data_lake.missing_ns"`), include those names in their messages, reject `null` specs with `InvalidCatalogModelException` / `InvalidNamespaceModelException`, and preserve causes; `CatalogOperationException` and `ModelParseException` also preserve causes. |
| `TC-015` | Template-method managers, platform-provisioned catalog enforcement & idempotency (in-memory subclasses) | Separate in-memory `NamespaceManager` and `TableManager` subclasses invoked with `namespace.yaml` and `table.yaml` fixtures before and after the platform provisions the catalog | Before the catalog exists, `namespaceExists(Path)`, `namespaceExists(NamespaceModel)`, `createNamespace(Path)`, and `createTable(Path)` all throw `CatalogNotFoundException` (carrying `catalogModel`) and never auto-create the catalog; once the platform provisions the catalog, `createTable(Path)` before namespace creation throws `NamespaceNotFoundException`, `createNamespace(Path)` twice succeeds idempotently and both `namespaceExists(Path)` and `namespaceExists(NamespaceModel)` flip from `false` to `true`, and `createTable(Path)` twice succeeds idempotently. |
| `TC-016` | Concurrent parser usage | Invoke `YamlFileModelManager.INSTANCE.parseTable(path)` from multiple threads concurrently | All threads return equal `TableModel` objects with zero errors. |
| `TC-017` | Build & module hygiene | Run `mvn clean test` across the reactor | `lakehouse-core` has no Spark/Iceberg dependencies in `pom.xml`, compiles cleanly on Java 17, and all tests pass. |
| `TC-018` | Encapsulation & template modifier checks | Inspect visibility and modifiers of `FileModelManager`, `YamlFileModelManager`, `TableManager`, and `NamespaceManager` | `FileModelManager` (declaring `parseTable`, `parseNamespace`, `readFile`) and `YamlFileModelManager` (`final class implements FileModelManager`) are package-private in `...core.manager` (no `...core.parser` package); `TableManager` and `NamespaceManager` are `public abstract class` with `public final` template methods and `protected abstract` `do*` hooks. |
| `TC-019` | Parse, semantic & null failures never invoke subclass hooks | Pass `null` `Path`, missing/malformed YAML, semantically invalid YAML, `namespaceExists((NamespaceModel) null)`, and a custom internal `FileModelManager` returning `null` to `TableManager` / `NamespaceManager` subclasses | Expected `ModelParseException` / `Invalid*ModelException` is thrown and hook invocation counts remain `0`; passing `null` `FileModelManager` to the package-private constructor throws `IllegalArgumentException`. |
