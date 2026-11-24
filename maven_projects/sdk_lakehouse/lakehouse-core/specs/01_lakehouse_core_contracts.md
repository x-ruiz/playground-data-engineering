# Feature Spec: `lakehouse-core` Contracts, Composable Specs & YAML Parsers

- **Status:** Approved
- **Module:** `lakehouse-core` (Pure Java 17 — Engine-Agnostic)
- **Archetype:** Java Library Contract
- **Tier:** Mini-Spec
- **Owner:** Xavier Ruiz

---

## 1. Goal & Context

### Problem Statement
`lakehouse-core` needs a strict, self-validating, engine-agnostic contract layer before building engine-specific adapters (such as `lakehouse-iceberg`). Repeating raw `catalog` and `namespace` strings across specs and service calls invites silent typos and duplicates validation. Instead, specs should form an **acyclic, top-down immutable object chain** (`CatalogSpec` → `NamespaceSpec` → `TableSpec` → `ColumnSpec`) so that:
1. Callers reference a platform-provisioned catalog via an immutable `CatalogSpec` created once, pass it into `NamespaceSpec`, and pass `NamespaceSpec` into `TableSpec`—eliminating repeated raw identifier strings.
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
  - `LakehouseSpec` (`sealed interface`), `CatalogSpec`, `NamespaceSpec`, `TableSpec`, `ColumnSpec`, `ColumnType`
- Engine-agnostic `abstract` Template-Method manager base classes in `...core.manager`:
  - `TableManager` (`public final createTable(Path)` → `protected abstract doCreateTable(TableSpec)`)
  - `NamespaceManager` (`public final createNamespace(Path)` → `protected abstract doCreateNamespace(NamespaceSpec)`, `public final namespaceExists(Path)` & `public final namespaceExists(NamespaceSpec)` → `protected abstract doNamespaceExists(NamespaceSpec)`)
- Package-private stateless YAML file parser + test fixtures (internal to `...core.manager`, hidden from calling libraries):
  - Package-private `FileSpecManager` interface (`parseTable(Path)`, `parseNamespace(Path)`, `readFile(Path, Class<D>, String)`)
  - Package-private `YamlFileSpecManager` (`final class` implementing `FileSpecManager`) overriding `parseTable`, `parseNamespace`, and `readFile`
- Custom unchecked exception hierarchy rooted at `LakehouseException`.
- Cleanup of `lakehouse-core`:
  - Replace `Column` / `ColumnDefinition` with `ColumnSpec`.
  - Remove unused `spark-sql_2.12` dependency from `lakehouse-core/pom.xml`.
  - Update `lakehouse-iceberg/src/.../Main.java` only as needed to keep the multi-module Maven build compiling.

### Non-Goals (Do Not Implement)
- **No public YAML parser API or public `createTable(TableSpec)` / `createNamespace(NamespaceSpec)` entry points:** Calling libraries interact with `TableManager` and `NamespaceManager` via format-agnostic `Path` methods (plus `namespaceExists(NamespaceSpec)`); YAML parsers remain strictly package-private inside `...core.manager` and raw `*Spec` creation hooks stay `protected abstract`.
- **No `CatalogManager`, no catalog YAML parser, and no catalog/namespace properties:** Catalogs must be provisioned externally by the platform/infrastructure—never via declarative YAML and never auto-created by `lakehouse-core` services. Catalog connection configs and engine-specific namespace properties belong in engine modules (or a future overloaded API), keeping `CatalogSpec` and `NamespaceSpec` pure identity contracts.
- **No `lakehouse-iceberg` implementation:** Do NOT implement `IcebergTableManager`, `IcebergNamespaceManager`, `IcebergTypeMapper`, or catalog factories in this spec.
- **No engine catalog/connection types in `lakehouse-core`:** `TableManager` and `NamespaceManager` must not reference Iceberg `Catalog` or any engine-specific client.
- **No Protobuf/gRPC parsing implementation:** Keep `ProtoFileTableParser` untouched for a future spec.
- **No `dropTable`, `tableExists`, `dropNamespace`, or `alterTable`:** Only the methods explicitly listed in Section 4.
- **No schema drift detection or schema evolution:** Idempotent `createTable` on an existing table follows pure `CREATE TABLE IF NOT EXISTS` semantics (no-op).
- **No parameterized or nested column types yet:** Types like `DECIMAL(10, 2)`, `ARRAY<T>`, `MAP<K, V>`, or `STRUCT<...>` are deferred to a future spec.
- **No partition transforms:** Only single identity partition column (`partitionColumn`); transforms like `day(date)` or multi-column partitioning are deferred.

---

## 3. Functional Requirements

### Composable Domain Models & Validation
- `REQ-01`: The library shall define a `sealed interface LakehouseSpec` (`permits CatalogSpec, NamespaceSpec, TableSpec`) declaring `name()` and `fullyQualifiedName()`, and housing the shared identifier validation helper (`validateIdentifier(...)`).
- `REQ-02`: `CatalogSpec(name)` shall represent a validated, immutable catalog reference (name only; catalog connection/infrastructure properties belong in engine modules) and return `name` from `fullyQualifiedName()`.
- `REQ-03`: `NamespaceSpec(catalog, name)` shall compose a non-null `CatalogSpec`, validate `name`, and return `"<catalog>.<namespace>"` from `fullyQualifiedName()`.
- `REQ-04`: `TableSpec(namespace, name, columns, partitionColumn)` shall compose a non-null `NamespaceSpec`, validate `name`, expose convenience `catalog()` delegating to `namespace.catalog()`, and return `"<catalog>.<namespace>.<tableName>"` from `fullyQualifiedName()`.
- `REQ-05`: All identifier segments (`CatalogSpec.name`, `NamespaceSpec.name`, `TableSpec.name`, `ColumnSpec.name`) shall be non-null, non-blank, and match `^[a-zA-Z_][a-zA-Z0-9_]*$` (disallowing dots, whitespace, and special characters).
- `REQ-06`: The library shall define a closed canonical `ColumnType` enum (`STRING`, `INT`, `BIGINT`, `DOUBLE`, `BOOLEAN`, `DATE`, `TIMESTAMP`, `DECIMAL`) with case-insensitive, whitespace-trimmed parsing via `ColumnType.fromString(String)`.
- `REQ-07`: `ColumnSpec(name, type, nullable)` shall be a standalone, immutable leaf record (not implementing `LakehouseSpec` and holding no back-reference to `TableSpec`) validating `name` via `LakehouseSpec.validateIdentifier(...)` and non-null `type`.
- `REQ-08`: `TableSpec` shall enforce at least one column, reject `null` column entries, reject duplicate column names (case-insensitively), and defensively copy its `columns` list so callers cannot mutate internal state.
- `REQ-09`: `TableSpec.partitionColumn` shall be optional (`null` represents an unpartitioned table). When non-null, it shall be non-blank and match the `name` of an existing column in `TableSpec.columns` (case-insensitively).

### Manager Template Contracts
- `REQ-10`: `TableManager` shall be a `public abstract class` exposing `public final void createTable(Path sourcePath)` which parses `sourcePath` via its internal `FileSpecManager` (defaulting to `YamlFileSpecManager.INSTANCE`), validates the parsed `TableSpec` is non-null, and delegates to `protected abstract void doCreateTable(TableSpec spec)`. Subclass implementations of `doCreateTable` shall be idempotent (no-op if the table already exists), and `doCreateTable` shall never be invoked if parsing or spec validation fails.
- `REQ-11`: `TableManager.doCreateTable(TableSpec)` implementations shall fail with `CatalogNotFoundException` (carrying `spec.catalog()`) if the target catalog does not exist, and with `NamespaceNotFoundException` (carrying `spec.namespace()`) if the target catalog exists but the target namespace does not exist; implementations shall never auto-create missing catalogs or namespaces.
- `REQ-12`: `NamespaceManager` shall be a `public abstract class` exposing `public final void createNamespace(Path sourcePath)` which parses `sourcePath` via its internal `FileSpecManager` (defaulting to `YamlFileSpecManager.INSTANCE`), validates the parsed `NamespaceSpec` is non-null, and delegates to `protected abstract void doCreateNamespace(NamespaceSpec spec)`. Subclass implementations of `doCreateNamespace` shall fail with `CatalogNotFoundException` (carrying `spec.catalog()`) if the target catalog does not exist (never auto-creating missing catalogs) and be idempotent (no-op if the namespace already exists); `doCreateNamespace` shall never be invoked if parsing or validation fails.
- `REQ-13`: `NamespaceManager` shall expose `public final boolean namespaceExists(Path sourcePath)` (parsing `sourcePath` into a `NamespaceSpec` and delegating to `namespaceExists(NamespaceSpec)`) and `public final boolean namespaceExists(NamespaceSpec spec)` (rejecting `null` `spec` with `InvalidNamespaceSpecException` and delegating to `protected abstract boolean doNamespaceExists(NamespaceSpec spec)`). Subclass implementations of `doNamespaceExists` shall fail with `CatalogNotFoundException` (carrying `spec.catalog()`) if the target catalog does not exist and return whether the target namespace currently exists; `doNamespaceExists` shall never be invoked if parsing or null validation fails.

### Internal YAML File Parsers (Package-Private)
- `REQ-14`: `YamlFileSpecManager.parseTable(Path)` shall parse a YAML file at a given `java.nio.file.Path` into a validated `TableSpec` (composing `CatalogSpec` → `NamespaceSpec` → `TableSpec` → `List<ColumnSpec>`), supporting keys `catalog`, `namespace`, `tableName`, `columns` (`name`, `type`, `nullable`), and optional `partitionColumn`.
- `REQ-15`: When `nullable` is omitted for a column in YAML, `YamlFileSpecManager.parseTable(Path)` shall default `nullable` to `true`.
- `REQ-16`: `YamlFileSpecManager.parseNamespace(Path)` shall parse a YAML file at a given `java.nio.file.Path` into a validated `NamespaceSpec` (composing `CatalogSpec` → `NamespaceSpec`), supporting keys `catalog` and `namespace`.
- `REQ-17`: `FileSpecManager` (declaring `parseTable(Path)`, `parseNamespace(Path)`, and `readFile(Path, Class<D>, String)`) and `YamlFileSpecManager` (`final class` implementing `FileSpecManager` and overriding all three methods) shall be **package-private** inside `...lakehouse.core.manager` (never `public`, and never exposed in any `public` or `protected` manager signature). `YamlFileSpecManager` shall reject unknown YAML fields (fail-fast on typos like `partitonColumn`), keep Jackson annotations out of domain records (via internal DTOs), and remain stateless and thread-safe. Both managers shall expose a `protected` no-arg constructor (wiring `YamlFileSpecManager.INSTANCE`) and a package-private constructor accepting `FileSpecManager` (failing fast on `null` parser) for internal testability and future format routing.

### Error Handling
- `REQ-18`: All library domain and parsing failures shall throw unchecked exceptions extending `LakehouseException` rather than raw `RuntimeException` or error-status DTOs.

---

## 4. Public Java API Contract

### Package Layout
- `x.ruiz.playground.data.engineering.lakehouse.core.spec` — Composable immutable spec models and canonical `ColumnType`.
- `x.ruiz.playground.data.engineering.lakehouse.core.manager` — Engine-agnostic `abstract` Template-Method manager base classes (`TableManager`, `NamespaceManager`) plus **package-private** internal parsers (`FileSpecManager`, `YamlFileSpecManager`).
- `x.ruiz.playground.data.engineering.lakehouse.core.exception` — Custom exception hierarchy.

### Composable Hierarchy Overview
`CatalogSpec` → composed by `NamespaceSpec` → composed by `TableSpec` → owns `List<ColumnSpec>` (strictly one-directional, zero circular references).

### Domain Models (`...lakehouse.core.spec`)

| Type | Kind | Fields / Values | Required Methods & Behavior |
| :--- | :--- | :--- | :--- |
| `LakehouseSpec` | `sealed interface` (`permits CatalogSpec, NamespaceSpec, TableSpec`) | — | Declares `name()` (`String`) and `fullyQualifiedName()` (`String`). Houses static `validateIdentifier(fieldName, value, exceptionFactory)` enforcing `^[a-zA-Z_][a-zA-Z0-9_]*$`. |
| `CatalogSpec` | `record` (`implements LakehouseSpec`) | `name` (`String`) | Compact constructor validates `name` via `LakehouseSpec.validateIdentifier` (throws `InvalidCatalogSpecException`). Exposes `fullyQualifiedName()` returning `name`. |
| `NamespaceSpec` | `record` (`implements LakehouseSpec`) | `catalog` (`CatalogSpec`), `name` (`String`) | Compact constructor enforces non-null `catalog` and validates `name` via `LakehouseSpec.validateIdentifier` (throws `InvalidNamespaceSpecException`). Exposes `fullyQualifiedName()` returning `"<catalog>.<namespace>"`. |
| `TableSpec` | `record` (`implements LakehouseSpec`) | `namespace` (`NamespaceSpec`), `name` (`String`), `columns` (`List<ColumnSpec>`), `partitionColumn` (`String`, nullable) | Compact constructor enforces non-null `namespace`, validates `name` via `LakehouseSpec.validateIdentifier` (throws `InvalidTableSpecException`), enforces non-empty `columns` with no null or case-insensitively duplicate column names, defensive `List.copyOf`, and `partitionColumn` membership when non-null. Exposes `catalog()` (delegates to `namespace.catalog()`), `fullyQualifiedName()` (`"<catalog>.<namespace>.<tableName>"`), `isPartitioned()`, and `partitionColumnOptional()`. |
| `ColumnType` | `enum` | `STRING`, `INT`, `BIGINT`, `DOUBLE`, `BOOLEAN`, `DATE`, `TIMESTAMP`, `DECIMAL` | Static factory `fromString(String)` parses case-insensitively after trimming whitespace; throws `InvalidTableSpecException` on null, blank, or unknown values. |
| `ColumnSpec` | `record` *(leaf — not a `LakehouseSpec`)* | `name` (`String`), `type` (`ColumnType`), `nullable` (`boolean`) | Compact constructor validates `name` via `LakehouseSpec.validateIdentifier` and non-null `type` (throws `InvalidTableSpecException`). Holds no back-pointer to `TableSpec`. |

### Manager Template Base Classes (`...lakehouse.core.manager`)

| Type | Kind & Constructors | Operations | Contract |
| :--- | :--- | :--- | :--- |
| `TableManager` | `public abstract class`<br/>`protected TableManager()`<br/>*(package-private)* `TableManager(FileSpecManager)` | `public final void createTable(Path)`<br/>`protected abstract void doCreateTable(TableSpec)` | `createTable(Path)` parses via internal `FileSpecManager.parseTable(Path)` (default `YamlFileSpecManager.INSTANCE`), guards against `null` spec (`InvalidTableSpecException`), and invokes `doCreateTable(spec)`. `doCreateTable` fails with `CatalogNotFoundException` if `spec.catalog()` does not exist, fails with `NamespaceNotFoundException` if `spec.namespace()` does not exist, and is an idempotent no-op if the table already exists. |
| `NamespaceManager` | `public abstract class`<br/>`protected NamespaceManager()`<br/>*(package-private)* `NamespaceManager(FileSpecManager)` | `public final void createNamespace(Path)`<br/>`public final boolean namespaceExists(Path)`<br/>`public final boolean namespaceExists(NamespaceSpec)`<br/>`protected abstract void doCreateNamespace(NamespaceSpec)`<br/>`protected abstract boolean doNamespaceExists(NamespaceSpec)` | `Path` methods parse via internal `FileSpecManager.parseNamespace(Path)` (default `YamlFileSpecManager.INSTANCE`). `namespaceExists(NamespaceSpec)` rejects `null` with `InvalidNamespaceSpecException` before invoking `doNamespaceExists(spec)`. Both `do*` hooks fail with `CatalogNotFoundException` if `spec.catalog()` does not exist; `doCreateNamespace` is an idempotent no-op if namespace already exists. |

### Internal Parser Types (`...lakehouse.core.manager` — Package-Private Only)

| Type | Kind & Visibility | Operations | Contract |
| :--- | :--- | :--- | :--- |
| `FileSpecManager` | *(package-private)* `interface` | `parseTable(Path)` → `TableSpec`<br/>`parseNamespace(Path)` → `NamespaceSpec`<br/>`<D> D readFile(Path, Class<D>, String)` | Internal file-spec parser contract invisible outside `...core.manager`, enabling format-specific implementations (YAML today, `.proto` / `.textproto` in v2) behind the same `Path`-based manager methods. |
| `YamlFileSpecManager` | *(package-private)* `final class` (`implements FileSpecManager`) | `INSTANCE` singleton + package-private no-arg ctor; overrides `parseTable(Path)`, `parseNamespace(Path)`, and `readFile(Path, Class<D>, String)` | Stateless, thread-safe YAML parser using a shared strict `YAMLMapper` and private DTOs (no Jackson annotations on domain records); builds `CatalogSpec` → `NamespaceSpec` (→ `TableSpec`); rejects unknown YAML fields; defaults omitted column `nullable` to `true`. *(There is intentionally no `parseCatalog` method; catalogs are provisioned by the platform.)* |

### Template Execution Flow

```text
Caller (lakehouse-iceberg / application)
  │
  ├─► TableManager.createTable(Path)                    [public final]
  │     ├─► FileSpecManager.parseTable(Path)             [package-private → YamlFileSpecManager]
  │     │     ├─► readFile(Path, YamlTableDto.class, "table")
  │     │     └─► validates CatalogSpec → NamespaceSpec → TableSpec
  │     └─► doCreateTable(TableSpec)                    [protected abstract — engine subclass]
  │
  ├─► NamespaceManager.createNamespace(Path)            [public final]
  │     ├─► FileSpecManager.parseNamespace(Path)         [package-private → YamlFileSpecManager]
  │     │     ├─► readFile(Path, YamlNamespaceDto.class, "namespace")
  │     │     └─► validates CatalogSpec → NamespaceSpec
  │     └─► doCreateNamespace(NamespaceSpec)            [protected abstract — engine subclass]
  │
  └─► NamespaceManager.namespaceExists(Path | NamespaceSpec)  [public final]
        ├─► (if Path) FileSpecManager.parseNamespace(Path)
        ├─► non-null guard → InvalidNamespaceSpecException
        └─► doNamespaceExists(NamespaceSpec)            [protected abstract — engine subclass]
```

### Exception Hierarchy (`...lakehouse.core.exception`)

| Exception | Parent | Constructors / Extra Members | When Thrown |
| :--- | :--- | :--- | :--- |
| `LakehouseException` | `RuntimeException` (`abstract`) | `(String message)`, `(String message, Throwable cause)` | Base unchecked exception for all `lakehouse-core` domain and parsing errors. |
| `InvalidCatalogSpecException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Invalid `CatalogSpec` name or `null` `CatalogSpec` passed to `CatalogNotFoundException`. |
| `InvalidNamespaceSpecException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Invalid `NamespaceSpec` (null `CatalogSpec`, invalid name), `null` `NamespaceSpec` passed to `NamespaceManager.namespaceExists(NamespaceSpec)` or `NamespaceNotFoundException`, or `null` spec returned by an internal `FileSpecManager`. |
| `InvalidTableSpecException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Invalid `TableSpec` (null `NamespaceSpec`, invalid name, bad columns/partition), invalid `ColumnSpec`, invalid `ColumnType`, or `null` spec returned by an internal `FileSpecManager`. |
| `SpecParseException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Missing/unreadable file, `null` `Path`, empty file, malformed YAML syntax, or unknown YAML property. |
| `CatalogNotFoundException` | `LakehouseException` | `(CatalogSpec catalog)`, `(CatalogSpec catalog, Throwable cause)`; exposes `getCatalogSpec()` and `getFullyQualifiedName()` | Target catalog has not been provisioned by the platform during `NamespaceManager.createNamespace(...)`, `NamespaceManager.namespaceExists(...)`, or `TableManager.createTable(...)`. |
| `NamespaceNotFoundException` | `LakehouseException` | `(NamespaceSpec namespace)`, `(NamespaceSpec namespace, Throwable cause)`; exposes `getNamespaceSpec()` and `getFullyQualifiedName()` | Target namespace does not exist (within an existing catalog) during `TableManager.createTable(...)`. |
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
1. **Strict Immutability & Acyclicity:** All spec records are deeply immutable and strictly one-directional (`CatalogSpec` ← `NamespaceSpec` ← `TableSpec` → `ColumnSpec`). Mutating any caller `List` after constructing `TableSpec` must not affect the spec, and attempting to mutate `spec.columns()` must throw `UnsupportedOperationException`.
2. **Engine Independence:** Zero imports from `org.apache.iceberg.*` or `org.apache.spark.*` anywhere in `lakehouse-core`.
3. **Thread Safety:** `YamlFileSpecManager` holds no per-call mutable state (uses a single `private static final YAMLMapper` configured once) and is safe to invoke concurrently across threads.
4. **Strict Parser Encapsulation & Unbypassable Template Flow:** `FileSpecManager` and `YamlFileSpecManager` are package-private in `...core.manager`, invisible to calling libraries on both classpath and module-path runtimes. Because `createTable(Path)`, `createNamespace(Path)`, `namespaceExists(Path)`, and `namespaceExists(NamespaceSpec)` are `final`, no subclass can skip parsing or null checks, and `doCreateTable` / `doCreateNamespace` / `doNamespaceExists` are never called when parsing or validation fails.
5. **No Silent Catalog or Namespace Creation:** Catalogs must be pre-provisioned by the platform (never via declarative YAML). `NamespaceManager` and `TableManager` subclasses must reject missing catalogs with `CatalogNotFoundException` and never auto-create them; `TableManager.doCreateTable` must reject missing namespaces with `NamespaceNotFoundException`.
6. **No Exception Swallowing:** Every wrapped exception must preserve the original `Throwable cause`.

### Failure Mode Matrix

| Failure Condition | Expected Exception | Notes |
| :--- | :--- | :--- |
| `sourcePath` is `null` (via `createTable(Path)`, `createNamespace(Path)`, `namespaceExists(Path)`, or `YamlFileSpecManager`) | `SpecParseException` | Fail fast; subclass hooks are never invoked |
| File does not exist or cannot be read (`IOException`) | `SpecParseException` | Preserves `IOException` cause; subclass hooks not invoked |
| Malformed YAML syntax or unknown YAML property | `SpecParseException` | Preserves Jackson exception cause; subclass hooks not invoked |
| Empty YAML file (deserializes to `null`) | `SpecParseException` | Rejects empty/comment-only file; subclass hooks not invoked |
| Missing/blank/invalid `CatalogSpec.name` | `InvalidCatalogSpecException` | Applies to direct construction and YAML parsing; subclass hooks not invoked |
| `NamespaceSpec` given `null` `CatalogSpec` or invalid `name` | `InvalidNamespaceSpecException` | Applies to direct construction and YAML parsing; subclass hooks not invoked |
| `TableSpec` given `null` `NamespaceSpec`, invalid `name`, empty/null/duplicate `columns`, or invalid `partitionColumn` | `InvalidTableSpecException` | Duplicate column check is case-insensitive; `null` `partitionColumn` is valid (unpartitioned), blank string is invalid; subclass hooks not invoked |
| Invalid `ColumnSpec.name`, `null` `ColumnType`, or unknown/blank `ColumnType.fromString(...)` | `InvalidTableSpecException` | Message includes offending type value |
| `NamespaceManager.namespaceExists((NamespaceSpec) null)` | `InvalidNamespaceSpecException` | Enforced once by base class; `doNamespaceExists` not invoked |
| Package-private manager constructor given `null` `FileSpecManager` | `IllegalArgumentException` | Fail fast at construction |
| Custom internal `FileSpecManager` returns `null` spec | `InvalidTableSpecException` / `InvalidNamespaceSpecException` | Enforced by base class; `do*` hooks not invoked |
| Target catalog missing (not provisioned by platform) during `createNamespace`, `namespaceExists`, or `createTable` | `CatalogNotFoundException` | Exposes `getCatalogSpec()` and `getFullyQualifiedName()`; never auto-creates catalog |
| Target namespace missing (within an existing catalog) during `createTable` | `NamespaceNotFoundException` | Exposes `getNamespaceSpec()` and `getFullyQualifiedName()` |
| Underlying catalog / network / storage error | `CatalogOperationException` | Preserves underlying cause |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | Valid table YAML (happy path) | Existing `src/test/resources/table.yaml` | `YamlFileSpecManager.parseTable(path)` (via `FileSpecManager`) returns `TableSpec` with `fullyQualifiedName() == "data_lake.market_data.transactions"`, `namespace().fullyQualifiedName() == "data_lake.market_data"`, `catalog().fullyQualifiedName() == "data_lake"`, 3 typed `ColumnSpec`s (`DATE`, `BIGINT`, `DECIMAL`), and `partitionColumn == "date"` (`isPartitioned() == true`). |
| `TC-002` | Unpartitioned table & default nullability | Valid YAML omitting `partitionColumn` and omitting `nullable` on a column | Returns `TableSpec` with `isPartitioned() == false`, `partitionColumnOptional().isEmpty()`, and omitted `nullable` defaulted to `true`. |
| `TC-003` | Case-insensitive `ColumnType` parsing | YAML column `type: " date "` or `ColumnType.fromString("bigint")` | Parses cleanly to `ColumnType.DATE` / `ColumnType.BIGINT`. |
| `TC-004` | Invalid `ColumnType` | YAML column `type: "VARCHR"` or `null` | Throws `InvalidTableSpecException` mentioning invalid type. |
| `TC-005` | Invalid identifier format at each spec level | `name` is `null`, blank, or contains `.` / spaces (e.g. `"market.data"`) on `CatalogSpec`, `NamespaceSpec`, `TableSpec`, or `ColumnSpec` | Throws `InvalidCatalogSpecException`, `InvalidNamespaceSpecException`, or `InvalidTableSpecException` respectively. |
| `TC-006` | Null parent composition | `new NamespaceSpec(null, "ns")` or `new TableSpec(null, "tbl", cols, null)` | Throws `InvalidNamespaceSpecException` (null `CatalogSpec`) / `InvalidTableSpecException` (null `NamespaceSpec`)—never raw `NullPointerException`. |
| `TC-007` | Empty or duplicate columns | `TableSpec` with empty column list, `null` entry, or columns `["id", "ID"]` | Throws `InvalidTableSpecException`. |
| `TC-008` | Invalid `partitionColumn` | `partitionColumn = "non_existent"` or `partitionColumn = "  "` | Throws `InvalidTableSpecException`. |
| `TC-009` | Unknown YAML key in table YAML | YAML containing typo `partitonColumn: date` | `YamlFileSpecManager.parseTable(path)` throws `SpecParseException` (with Jackson cause). |
| `TC-010` | Missing / unreadable / malformed YAML file | Non-existent `Path`, `null` `Path`, empty file, or invalid YAML syntax | Both `YamlFileSpecManager.parseTable` and `YamlFileSpecManager.parseNamespace` throw `SpecParseException` with underlying cause when applicable. |
| `TC-011` | Valid namespace YAML (happy path) | Valid `src/test/resources/namespace.yaml` with `catalog` and `namespace` | `YamlFileSpecManager.parseNamespace(path)` (via `FileSpecManager`) returns `NamespaceSpec` with `catalog().fullyQualifiedName() == "data_lake"` and `fullyQualifiedName() == "data_lake.market_data"`. |
| `TC-012` | Invalid namespace YAML | Missing `catalog` or `namespace`, invalid identifier, or unknown YAML key | Throws `InvalidCatalogSpecException` / `InvalidNamespaceSpecException` (for semantic errors) or `SpecParseException` (for unknown key / syntax error). |
| `TC-013` | Defensive immutability | Mutate source `List` after constructing `TableSpec`, and attempt `.add()` on `spec.columns()` | Spec contents remain unchanged; direct mutation throws `UnsupportedOperationException`. |
| `TC-014` | `CatalogNotFoundException`, `NamespaceNotFoundException` & exception hierarchy | Construct `CatalogNotFoundException` with `new CatalogSpec("missing_catalog")` and `NamespaceNotFoundException` with `new NamespaceSpec(new CatalogSpec("data_lake"), "missing_ns")` | Both extend `LakehouseException`, expose their exact specs (`getCatalogSpec()` / `getNamespaceSpec()`), `getFullyQualifiedName()` (`"missing_catalog"` / `"data_lake.missing_ns"`), include those names in their messages, reject `null` specs with `InvalidCatalogSpecException` / `InvalidNamespaceSpecException`, and preserve causes; `CatalogOperationException` and `SpecParseException` also preserve causes. |
| `TC-015` | Template-method managers, platform-provisioned catalog enforcement & idempotency (in-memory subclasses) | Separate in-memory `NamespaceManager` and `TableManager` subclasses invoked with `namespace.yaml` and `table.yaml` fixtures before and after the platform provisions the catalog | Before the catalog exists, `namespaceExists(Path)`, `namespaceExists(NamespaceSpec)`, `createNamespace(Path)`, and `createTable(Path)` all throw `CatalogNotFoundException` (carrying `catalogSpec`) and never auto-create the catalog; once the platform provisions the catalog, `createTable(Path)` before namespace creation throws `NamespaceNotFoundException`, `createNamespace(Path)` twice succeeds idempotently and both `namespaceExists(Path)` and `namespaceExists(NamespaceSpec)` flip from `false` to `true`, and `createTable(Path)` twice succeeds idempotently. |
| `TC-016` | Concurrent parser usage | Invoke `YamlFileSpecManager.INSTANCE.parseTable(path)` from multiple threads concurrently | All threads return equal `TableSpec` objects with zero errors. |
| `TC-017` | Build & module hygiene | Run `mvn clean test` across the reactor | `lakehouse-core` has no Spark/Iceberg dependencies in `pom.xml`, compiles cleanly on Java 17, and all tests pass. |
| `TC-018` | Encapsulation & template modifier checks | Inspect visibility and modifiers of `FileSpecManager`, `YamlFileSpecManager`, `TableManager`, and `NamespaceManager` | `FileSpecManager` (declaring `parseTable`, `parseNamespace`, `readFile`) and `YamlFileSpecManager` (`final class implements FileSpecManager`) are package-private in `...core.manager` (no `...core.parser` package); `TableManager` and `NamespaceManager` are `public abstract class` with `public final` template methods and `protected abstract` `do*` hooks. |
| `TC-019` | Parse, semantic & null failures never invoke subclass hooks | Pass `null` `Path`, missing/malformed YAML, semantically invalid YAML, `namespaceExists((NamespaceSpec) null)`, and a custom internal `FileSpecManager` returning `null` to `TableManager` / `NamespaceManager` subclasses | Expected `SpecParseException` / `Invalid*SpecException` is thrown and hook invocation counts remain `0`; passing `null` `FileSpecManager` to the package-private constructor throws `IllegalArgumentException`. |
