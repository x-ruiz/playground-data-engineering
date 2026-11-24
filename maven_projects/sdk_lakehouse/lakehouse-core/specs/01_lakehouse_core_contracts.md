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
1. Callers create an immutable `CatalogSpec` once, pass it into `NamespaceSpec`, and pass `NamespaceSpec` into `TableSpec`—eliminating repeated raw identifier strings.
2. Declarative YAML specifications for **namespaces** and **tables** (GitOps pattern) are parsed into the exact same validated spec hierarchy fail-fast before any catalog call occurs.
3. Downstream engine modules (`lakehouse-iceberg`, future Delta/BigLake modules) implement clean, uniform, idempotent service interfaces (`TableManager`, `NamespaceManager`) against a closed set of canonical column types and deterministic exception semantics.

### Success Metrics
- `lakehouse-core` compiles and passes all unit tests (`mvn test -pl lakehouse-core`) with **zero** Iceberg or Spark dependencies.
- Every acceptance case (`TC-001` through `TC-017`) is covered by deterministic JUnit 5 + AssertJ tests.
- All public contracts are immutable, strictly acyclic, thread-safe, and free of serialization annotations (`@JsonProperty`, etc.) on domain models.

---

## 2. Scope & Explicit Non-Goals

### In Scope
- Engine-agnostic composable domain models and canonical type enum in `lakehouse-core`:
  - `LakehouseSpec` (`sealed interface`), `CatalogSpec`, `NamespaceSpec`, `TableSpec`, `ColumnSpec`, `ColumnType`
- Engine-agnostic service interfaces:
  - `TableManager` (`createTable` only)
  - `NamespaceManager` (`createNamespace`, `namespaceExists`)
- Stateless YAML file parsers + test fixtures:
  - Single generic `FileParser<T extends LakehouseSpec>` interface implemented by `YamlFileTableParser` (`FileParser<TableSpec>`) and `YamlFileNamespaceParser` (`FileParser<NamespaceSpec>`)
- Custom unchecked exception hierarchy rooted at `LakehouseException`.
- Cleanup of `lakehouse-core`:
  - Replace `Column` / `ColumnDefinition` with `ColumnSpec`.
  - Remove unused `spark-sql_2.12` dependency from `lakehouse-core/pom.xml`.
  - Update `lakehouse-iceberg/src/.../Main.java` only as needed to keep the multi-module Maven build compiling against the updated `FileParser` signature.

### Non-Goals (Do Not Implement)
- **No `CatalogManager` or catalog/namespace properties:** Catalog connection configs and engine-specific namespace properties belong in engine modules (or a future overloaded API), keeping `CatalogSpec` and `NamespaceSpec` pure identity contracts.
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

### Service Contracts (Ports)
- `REQ-10`: `TableManager.createTable(TableSpec)` shall return `void` and be idempotent (no-op if the table already exists).
- `REQ-11`: `TableManager.createTable(TableSpec)` shall fail with `NamespaceNotFoundException` (carrying `spec.namespace()`) if the target namespace does not exist; implementations shall never auto-create missing namespaces.
- `REQ-12`: `NamespaceManager.createNamespace(NamespaceSpec)` shall return `void` and be idempotent (no-op if the namespace already exists).
- `REQ-13`: `NamespaceManager.namespaceExists(NamespaceSpec)` shall accept a non-null `NamespaceSpec` (reusing the immutable spec rather than raw strings) and return whether the target namespace currently exists.

### YAML File Parsers
- `REQ-14`: `YamlFileTableParser` shall parse a YAML file at a given `java.nio.file.Path` into a validated `TableSpec` (composing `CatalogSpec` → `NamespaceSpec` → `TableSpec` → `List<ColumnSpec>`), supporting keys `catalog`, `namespace`, `tableName`, `columns` (`name`, `type`, `nullable`), and optional `partitionColumn`.
- `REQ-15`: When `nullable` is omitted for a column in YAML, `YamlFileTableParser` shall default `nullable` to `true`.
- `REQ-16`: `YamlFileNamespaceParser` shall parse a YAML file at a given `java.nio.file.Path` into a validated `NamespaceSpec` (composing `CatalogSpec` → `NamespaceSpec`), supporting keys `catalog` and `namespace`.
- `REQ-17`: Both YAML parsers shall reject unknown YAML fields (fail-fast on typos like `partitonColumn`), keep Jackson annotations out of domain records (via internal DTOs), and remain stateless and thread-safe.

### Error Handling
- `REQ-18`: All library domain and parsing failures shall throw unchecked exceptions extending `LakehouseException` rather than raw `RuntimeException` or error-status DTOs.

---

## 4. Public Java API Contract

### Package Layout
- `x.ruiz.playground.data.engineering.lakehouse.core` — Composable spec models, service interfaces, and parsers.
- `x.ruiz.playground.data.engineering.lakehouse.core.exception` — Custom exception hierarchy.

### Composable Hierarchy Overview
`CatalogSpec` → composed by `NamespaceSpec` → composed by `TableSpec` → owns `List<ColumnSpec>` (strictly one-directional, zero circular references).

### Domain Models (`...lakehouse.core`)

| Type | Kind | Fields / Values | Required Methods & Behavior |
| :--- | :--- | :--- | :--- |
| `LakehouseSpec` | `sealed interface` (`permits CatalogSpec, NamespaceSpec, TableSpec`) | — | Declares `name()` (`String`) and `fullyQualifiedName()` (`String`). Houses static `validateIdentifier(fieldName, value, exceptionFactory)` enforcing `^[a-zA-Z_][a-zA-Z0-9_]*$`. |
| `CatalogSpec` | `record` (`implements LakehouseSpec`) | `name` (`String`) | Compact constructor validates `name` via `LakehouseSpec.validateIdentifier` (throws `InvalidCatalogSpecException`). Exposes `fullyQualifiedName()` returning `name`. |
| `NamespaceSpec` | `record` (`implements LakehouseSpec`) | `catalog` (`CatalogSpec`), `name` (`String`) | Compact constructor enforces non-null `catalog` and validates `name` via `LakehouseSpec.validateIdentifier` (throws `InvalidNamespaceSpecException`). Exposes `fullyQualifiedName()` returning `"<catalog>.<namespace>"`. |
| `TableSpec` | `record` (`implements LakehouseSpec`) | `namespace` (`NamespaceSpec`), `name` (`String`), `columns` (`List<ColumnSpec>`), `partitionColumn` (`String`, nullable) | Compact constructor enforces non-null `namespace`, validates `name` via `LakehouseSpec.validateIdentifier` (throws `InvalidTableSpecException`), enforces non-empty `columns` with no null or case-insensitively duplicate column names, defensive `List.copyOf`, and `partitionColumn` membership when non-null. Exposes `catalog()` (delegates to `namespace.catalog()`), `fullyQualifiedName()` (`"<catalog>.<namespace>.<tableName>"`), `isPartitioned()`, and `partitionColumnOptional()`. |
| `ColumnType` | `enum` | `STRING`, `INT`, `BIGINT`, `DOUBLE`, `BOOLEAN`, `DATE`, `TIMESTAMP`, `DECIMAL` | Static factory `fromString(String)` parses case-insensitively after trimming whitespace; throws `InvalidTableSpecException` on null, blank, or unknown values. |
| `ColumnSpec` | `record` *(leaf — not a `LakehouseSpec`)* | `name` (`String`), `type` (`ColumnType`), `nullable` (`boolean`) | Compact constructor validates `name` via `LakehouseSpec.validateIdentifier` and non-null `type` (throws `InvalidTableSpecException`). Holds no back-pointer to `TableSpec`. |

### Service Interfaces (`...lakehouse.core`)

| Interface | Operations | Contract |
| :--- | :--- | :--- |
| `TableManager` | `createTable(TableSpec)` → `void` | Rejects `null` spec with `InvalidTableSpecException`; fails with `NamespaceNotFoundException` if `spec.namespace()` does not exist; idempotent no-op if table already exists. |
| `NamespaceManager` | `createNamespace(NamespaceSpec)` → `void`<br/>`namespaceExists(NamespaceSpec)` → `boolean` | Both reject `null` spec with `InvalidNamespaceSpecException`; `createNamespace` is an idempotent no-op if namespace already exists. |

### Parser Interface & Implementations (`...lakehouse.core`)

| Type | Kind | Operations | Contract |
| :--- | :--- | :--- | :--- |
| `FileParser<T extends LakehouseSpec>` | `interface` | `parseData(Path)` → `T` | Single generic file-parser contract parameterized by any `LakehouseSpec` subtype. |
| `YamlFileTableParser` | `class` (implements `FileParser<TableSpec>`) | No-arg constructor; `parseData(Path)` → `TableSpec` | Stateless, thread-safe YAML parser using internal DTOs (no Jackson annotations on domain records); builds `CatalogSpec` → `NamespaceSpec` → `TableSpec`; rejects unknown YAML fields; defaults omitted `nullable` to `true`. |
| `YamlFileNamespaceParser` | `class` (implements `FileParser<NamespaceSpec>`) | No-arg constructor; `parseData(Path)` → `NamespaceSpec` | Stateless, thread-safe YAML parser using internal DTOs; builds `CatalogSpec` → `NamespaceSpec`; rejects unknown YAML fields. |

### Exception Hierarchy (`...lakehouse.core.exception`)

| Exception | Parent | Constructors / Extra Members | When Thrown |
| :--- | :--- | :--- | :--- |
| `LakehouseException` | `RuntimeException` (`abstract`) | `(String message)`, `(String message, Throwable cause)` | Base unchecked exception for all `lakehouse-core` domain and parsing errors. |
| `InvalidCatalogSpecException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Invalid `CatalogSpec` name. |
| `InvalidNamespaceSpecException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Invalid `NamespaceSpec` (null `CatalogSpec`, invalid name) or `null` input to `NamespaceManager`. |
| `InvalidTableSpecException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Invalid `TableSpec` (null `NamespaceSpec`, invalid name, bad columns/partition), invalid `ColumnSpec`, invalid `ColumnType`, or `null` input to `TableManager`. |
| `SpecParseException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Missing/unreadable file, `null` `Path`, empty file, malformed YAML syntax, or unknown YAML property. |
| `NamespaceNotFoundException` | `LakehouseException` | `(NamespaceSpec namespace)`, `(NamespaceSpec namespace, Throwable cause)`; exposes `getNamespaceSpec()` and `getFullyQualifiedName()` | Target namespace does not exist during `TableManager.createTable(...)`. |
| `CatalogOperationException` | `LakehouseException` | `(String message)`, `(String message, Throwable cause)` | Unexpected catalog, network, or storage failure in a `TableManager` / `NamespaceManager` implementation. |

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
3. **Thread Safety:** `YamlFileTableParser` and `YamlFileNamespaceParser` hold no per-call mutable state (configure `YAMLMapper` once at construction or via `static final`) and are safe to share across threads.
4. **No Silent Namespace Creation:** `TableManager.createTable` implementations must reject missing namespaces with `NamespaceNotFoundException`.
5. **No Exception Swallowing:** Every wrapped exception must preserve the original `Throwable cause`.

### Failure Mode Matrix

| Failure Condition | Expected Exception | Notes |
| :--- | :--- | :--- |
| `sourcePath` is `null` | `SpecParseException` | Fail fast with descriptive message |
| File does not exist or cannot be read (`IOException`) | `SpecParseException` | Preserves `IOException` cause |
| Malformed YAML syntax or unknown YAML property | `SpecParseException` | Preserves Jackson exception cause |
| Empty YAML file (deserializes to `null`) | `SpecParseException` | Rejects empty/comment-only file |
| Missing/blank/invalid `CatalogSpec.name` | `InvalidCatalogSpecException` | Applies to direct construction and YAML parsing |
| `NamespaceSpec` given `null` `CatalogSpec` or invalid `name` | `InvalidNamespaceSpecException` | Applies to direct construction and YAML parsing |
| `TableSpec` given `null` `NamespaceSpec`, invalid `name`, empty/null/duplicate `columns`, or invalid `partitionColumn` | `InvalidTableSpecException` | Duplicate column check is case-insensitive; `null` `partitionColumn` is valid (unpartitioned), blank string is invalid |
| Invalid `ColumnSpec.name`, `null` `ColumnType`, or unknown/blank `ColumnType.fromString(...)` | `InvalidTableSpecException` | Message includes offending type value |
| `TableManager.createTable(null)` | `InvalidTableSpecException` | Contract invariant for all implementations |
| `NamespaceManager.createNamespace(null)` / `namespaceExists(null)` | `InvalidNamespaceSpecException` | Contract invariant for all implementations |
| Target namespace missing during `createTable` | `NamespaceNotFoundException` | Exposes `getNamespaceSpec()` and `getFullyQualifiedName()` |
| Underlying catalog / network / storage error | `CatalogOperationException` | Preserves underlying cause |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | Valid table YAML (happy path) | Existing `src/test/resources/table.yaml` | `YamlFileTableParser.parseData(path)` returns `TableSpec` with `fullyQualifiedName() == "data_lake.market_data.transactions"`, `namespace().fullyQualifiedName() == "data_lake.market_data"`, `catalog().fullyQualifiedName() == "data_lake"`, 3 typed `ColumnSpec`s (`DATE`, `BIGINT`, `DECIMAL`), and `partitionColumn == "date"` (`isPartitioned() == true`). |
| `TC-002` | Unpartitioned table & default nullability | Valid YAML omitting `partitionColumn` and omitting `nullable` on a column | Returns `TableSpec` with `isPartitioned() == false`, `partitionColumnOptional().isEmpty()`, and omitted `nullable` defaulted to `true`. |
| `TC-003` | Case-insensitive `ColumnType` parsing | YAML column `type: " date "` or `ColumnType.fromString("bigint")` | Parses cleanly to `ColumnType.DATE` / `ColumnType.BIGINT`. |
| `TC-004` | Invalid `ColumnType` | YAML column `type: "VARCHR"` or `null` | Throws `InvalidTableSpecException` mentioning invalid type. |
| `TC-005` | Invalid identifier format at each spec level | `name` is `null`, blank, or contains `.` / spaces (e.g. `"market.data"`) on `CatalogSpec`, `NamespaceSpec`, `TableSpec`, or `ColumnSpec` | Throws `InvalidCatalogSpecException`, `InvalidNamespaceSpecException`, or `InvalidTableSpecException` respectively. |
| `TC-006` | Null parent composition | `new NamespaceSpec(null, "ns")` or `new TableSpec(null, "tbl", cols, null)` | Throws `InvalidNamespaceSpecException` (null `CatalogSpec`) / `InvalidTableSpecException` (null `NamespaceSpec`)—never raw `NullPointerException`. |
| `TC-007` | Empty or duplicate columns | `TableSpec` with empty column list, `null` entry, or columns `["id", "ID"]` | Throws `InvalidTableSpecException`. |
| `TC-008` | Invalid `partitionColumn` | `partitionColumn = "non_existent"` or `partitionColumn = "  "` | Throws `InvalidTableSpecException`. |
| `TC-009` | Unknown YAML key in table YAML | YAML containing typo `partitonColumn: date` | `YamlFileTableParser.parseData(path)` throws `SpecParseException` (with Jackson cause). |
| `TC-010` | Missing / unreadable / malformed YAML file | Non-existent `Path`, `null` `Path`, empty file, or invalid YAML syntax | Both `YamlFileTableParser` and `YamlFileNamespaceParser` throw `SpecParseException` with underlying cause when applicable. |
| `TC-011` | Valid namespace YAML (happy path) | Valid `src/test/resources/namespace.yaml` with `catalog` and `namespace` | `YamlFileNamespaceParser.parseData(path)` returns `NamespaceSpec` with `catalog().fullyQualifiedName() == "data_lake"` and `fullyQualifiedName() == "data_lake.market_data"`. |
| `TC-012` | Invalid namespace YAML | Missing `catalog` or `namespace`, invalid identifier, or unknown YAML key | Throws `InvalidCatalogSpecException` / `InvalidNamespaceSpecException` (for semantic errors) or `SpecParseException` (for unknown key / syntax error). |
| `TC-013` | Defensive immutability | Mutate source `List` after constructing `TableSpec`, and attempt `.add()` on `spec.columns()` | Spec contents remain unchanged; direct mutation throws `UnsupportedOperationException`. |
| `TC-014` | `NamespaceNotFoundException` & exception hierarchy | Construct `NamespaceNotFoundException` with `new NamespaceSpec(new CatalogSpec("data_lake"), "missing_ns")` | Extends `LakehouseException`, `getNamespaceSpec()` returns exact spec, `getFullyQualifiedName()` returns `"data_lake.missing_ns"`, message includes `"data_lake.missing_ns"`; `CatalogOperationException` and `SpecParseException` preserve cause. |
| `TC-015` | Service interface polymorphism & composable object reuse (in-memory test double) | Build `CatalogSpec` once → `NamespaceSpec` once → `TableSpec` once; run against in-memory `NamespaceManager` + `TableManager` | Creating table before namespace throws `NamespaceNotFoundException`; creating namespace twice succeeds idempotently and `namespaceExists(namespaceSpec)` returns `true`; creating table twice succeeds idempotently. |
| `TC-016` | Concurrent parser usage | Invoke a single `YamlFileTableParser` instance from multiple threads concurrently | All threads return equal `TableSpec` objects with zero errors. |
| `TC-017` | Build & module hygiene | Run `mvn clean test` across the reactor | `lakehouse-core` has no Spark/Iceberg dependencies in `pom.xml`, compiles cleanly on Java 17, and all tests pass. |
