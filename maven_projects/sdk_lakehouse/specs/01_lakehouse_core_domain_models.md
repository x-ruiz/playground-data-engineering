# Feature Spec: `lakehouse-core` Domain Models & Exception Hierarchy

- **Status:** Approved
- **Module / Component:** `lakehouse-core` (`...core.model`, `...core.exception` — Pure Java 17)
- **Archetype:** Java Library Contract
- **Tier:** Mini-Spec
- **Owner:** Xavier Ruiz
- **Depends On:** `specs/00_project_structure_and_organization.md`

---

## 1. Goal & Context

### Problem Statement
`lakehouse-core` needs a strict, self-validating, engine-agnostic domain model layer before introducing manager abstractions or engine adapters (`lakehouse-iceberg`). Passing raw `catalog` and `namespace` strings across APIs invites typos and duplicated validation. Modeling lakehouse entities as an **acyclic, top-down immutable record chain** (`CatalogModel` ← `NamespaceModel` ← `TableModel` → `ColumnModel`) with a canonical `ColumnType` enum and typed unchecked exceptions guarantees that invalid domain objects cannot exist.

### Success Metrics
- `lakehouse-core` compiles and passes all domain model and exception tests (`mvn test -pl lakehouse-core`) with **zero** Iceberg, Spark, or cloud SDK dependencies.
- All domain records are deeply immutable, strictly acyclic, and free of serialization annotations (`@Json*`).
- Full verification coverage of `TC-001` through `TC-009`.

---

## 2. Scope & Explicit Non-Goals

### In Scope
- Composable domain models and canonical type enum in `...lakehouse.core.model`:
  - `LakehouseModel` (`sealed interface`), `CatalogModel`, `NamespaceModel`, `TableModel`, `ColumnModel`, `ColumnType`.
- Unchecked exception hierarchy in `...lakehouse.core.exception` rooted at `LakehouseException`.
- Cleanup of `lakehouse-core`: replace legacy `Column` / `ColumnDefinition` with `ColumnModel`, and remove unused `spark-sql_2.12` from `lakehouse-core/pom.xml`.

### Non-Goals (Do Not Implement)
- **No Template-Method managers or YAML parsing:** `TableManager`, `NamespaceManager`, and encapsulated YAML parsing live in Spec 02.
- **No catalog/namespace property maps:** `CatalogModel` and `NamespaceModel` remain pure identity contracts; engine connection/storage properties belong in engine modules.
- **No parameterized or nested column types yet:** `DECIMAL(p, s)`, `ARRAY<T>`, `MAP<K, V>`, and `STRUCT<...>` are deferred.
- **No partition transforms or multi-column partitioning:** Only an optional single identity `partitionColumn`.
- **No engine or serialization dependencies in domain models:** Zero Jackson, Protobuf, Iceberg, or Spark types in `...core.model` or `...core.exception`.

---

## 3. Functional Requirements

- `REQ-01`: `LakehouseModel` shall be a `public sealed interface` (`permits CatalogModel, NamespaceModel, TableModel`) declaring `name()` and `fullyQualifiedName()`, and providing shared identifier validation (`^[a-zA-Z_][a-zA-Z0-9_]*$`, non-null, non-blank).
- `REQ-02`: `CatalogModel(name)` shall validate `name` (throwing `InvalidCatalogModelException`) and return `name` from `fullyQualifiedName()`.
- `REQ-03`: `NamespaceModel(catalog, name)` shall reject `null` `catalog` and invalid `name` (throwing `InvalidNamespaceModelException`) and return `"<catalog>.<namespace>"` from `fullyQualifiedName()`.
- `REQ-04`: `TableModel(namespace, name, columns, partitionColumn)` shall reject `null` `namespace` and invalid `name` (throwing `InvalidTableModelException`), expose `catalog()` delegating to `namespace.catalog()`, and return `"<catalog>.<namespace>.<tableName>"` from `fullyQualifiedName()`.
- `REQ-05`: `ColumnType` shall be a closed canonical enum (`STRING`, `INT`, `BIGINT`, `DOUBLE`, `BOOLEAN`, `DATE`, `TIMESTAMP`, `DECIMAL`) with case-insensitive, whitespace-trimmed `fromString(String)` throwing `InvalidTableModelException` on null, blank, or unknown values.
- `REQ-06`: `ColumnModel(name, type, nullable)` shall be a standalone leaf record (not implementing `LakehouseModel`, no back-pointer to `TableModel`) validating `name` and non-null `type` (throwing `InvalidTableModelException`).
- `REQ-07`: `TableModel` shall require at least one column, reject `null` entries and case-insensitively duplicate column names, defensively copy `columns` (`List.copyOf`), and validate optional `partitionColumn` (`null` = unpartitioned; when non-null, non-blank and case-insensitively matching an existing column).
- `REQ-08`: All library domain, parsing, and catalog failures shall extend an `abstract` unchecked `LakehouseException` preserving original `Throwable` causes.

---

## 4. Public Java API Contract

### Domain Models (`x.ruiz.playground.data.engineering.lakehouse.core.model`)

| Type | Kind | Fields / Values | Required Methods & Behavior |
| :--- | :--- | :--- | :--- |
| `LakehouseModel` | `public sealed interface` (`permits CatalogModel, NamespaceModel, TableModel`) | — | `name()`, `fullyQualifiedName()`, and shared static identifier validation enforcing `^[a-zA-Z_][a-zA-Z0-9_]*$`. |
| `CatalogModel` | `public record` (`implements LakehouseModel`) | `name` (`String`) | Validates `name` (`InvalidCatalogModelException`); `fullyQualifiedName()` returns `name`. |
| `NamespaceModel` | `public record` (`implements LakehouseModel`) | `catalog` (`CatalogModel`), `name` (`String`) | Rejects `null` `catalog` / invalid `name` (`InvalidNamespaceModelException`); `fullyQualifiedName()` returns `"<catalog>.<namespace>"`. |
| `TableModel` | `public record` (`implements LakehouseModel`) | `namespace` (`NamespaceModel`), `name` (`String`), `columns` (`List<ColumnModel>`), `partitionColumn` (`String`, nullable) | Validates parent, name, non-empty/unique columns, defensive copy, and optional `partitionColumn`; exposes `catalog()`, `fullyQualifiedName()`, `isPartitioned()`, and `partitionColumnOptional()`. |
| `ColumnType` | `public enum` | `STRING`, `INT`, `BIGINT`, `DOUBLE`, `BOOLEAN`, `DATE`, `TIMESTAMP`, `DECIMAL` | `fromString(String)` parses trimmed, case-insensitively; throws `InvalidTableModelException` on invalid input. |
| `ColumnModel` | `public record` *(leaf)* | `name` (`String`), `type` (`ColumnType`), `nullable` (`boolean`) | Validates `name` and non-null `type` (`InvalidTableModelException`); no back-reference to `TableModel`. |

### Exception Hierarchy (`x.ruiz.playground.data.engineering.lakehouse.core.exception`)

| Exception | Parent | Constructors / Members | When Thrown |
| :--- | :--- | :--- | :--- |
| `LakehouseException` | `RuntimeException` (`abstract`) | `(String)`, `(String, Throwable)` | Base unchecked exception for all SDK failures. |
| `InvalidCatalogModelException` | `LakehouseException` | `(String)`, `(String, Throwable)` | Invalid `CatalogModel` name or `null` catalog passed to `CatalogNotFoundException`. |
| `InvalidNamespaceModelException` | `LakehouseException` | `(String)`, `(String, Throwable)` | Invalid `NamespaceModel` or `null` namespace passed to `NamespaceNotFoundException` / manager guards. |
| `InvalidTableModelException` | `LakehouseException` | `(String)`, `(String, Throwable)` | Invalid `TableModel`, `ColumnModel`, or `ColumnType`. |
| `ModelParseException` | `LakehouseException` | `(String)`, `(String, Throwable)` | Missing, unreadable, empty, or malformed declarative spec file. |
| `CatalogNotFoundException` | `LakehouseException` | `(CatalogModel)`, `(CatalogModel, Throwable)`; `getCatalogModel()`, `getFullyQualifiedName()` | Target platform-provisioned catalog does not exist. |
| `NamespaceNotFoundException` | `LakehouseException` | `(NamespaceModel)`, `(NamespaceModel, Throwable)`; `getNamespaceModel()`, `getFullyQualifiedName()` | Target namespace does not exist within an existing catalog. |
| `CatalogOperationException` | `LakehouseException` | `(String)`, `(String, Throwable)` | Unexpected catalog, network, or storage failure. |

---

## 5. Invariants, Edge Cases & Failure Modes

### Key Invariants
1. **Deep Immutability & Acyclicity:** All records are strictly one-directional (`CatalogModel` ← `NamespaceModel` ← `TableModel` → `ColumnModel`); mutating caller lists never affects `TableModel`, and mutating `tableModel.columns()` throws `UnsupportedOperationException`.
2. **Engine & Serialization Isolation:** Zero imports from Jackson, Protobuf, Iceberg, or Spark in `...core.model`, `...core.exception`, or `lakehouse-core/pom.xml` (no Spark/Iceberg deps).
3. **No Raw `NullPointerException` Leaks:** Null parents, columns, or exception models throw typed `Invalid*ModelException` subclasses.
4. **Cause Preservation:** Every wrapped exception preserves its original `Throwable cause`.

### Failure Mode Matrix

| Failure Condition | Expected Exception | Notes |
| :--- | :--- | :--- |
| `name` is `null`, blank, or contains `.` / spaces / special chars | `InvalidCatalogModelException` / `InvalidNamespaceModelException` / `InvalidTableModelException` | Checked uniformly across all models and columns. |
| `NamespaceModel(null, ...)` or `TableModel(null, ...)` | `InvalidNamespaceModelException` / `InvalidTableModelException` | Never raw `NullPointerException`. |
| Empty `columns`, `null` entry, or duplicate names (`["id", "ID"]`) | `InvalidTableModelException` | Duplicate check is case-insensitive. |
| Blank `partitionColumn` or name not in `columns` | `InvalidTableModelException` | `null` `partitionColumn` is valid (unpartitioned). |
| Null, blank, or unknown `ColumnType.fromString(...)` | `InvalidTableModelException` | Message includes offending value. |
| `null` model passed to `CatalogNotFoundException` / `NamespaceNotFoundException` | `InvalidCatalogModelException` / `InvalidNamespaceModelException` | Fail fast at exception construction. |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | Valid composable model chain | `CatalogModel("data_lake")` → `NamespaceModel` → `TableModel` with 3 `ColumnModel`s & `partitionColumn = "date"` | `fullyQualifiedName()` returns `"data_lake"`, `"data_lake.market_data"`, and `"data_lake.market_data.transactions"`; `isPartitioned() == true`. |
| `TC-002` | Unpartitioned table | `TableModel` with `partitionColumn = null` | `isPartitioned() == false` and `partitionColumnOptional().isEmpty()`. |
| `TC-003` | Case-insensitive `ColumnType` parsing | `ColumnType.fromString(" date ")`, `ColumnType.fromString("bigint")` | Returns `ColumnType.DATE` and `ColumnType.BIGINT`. |
| `TC-004` | Invalid `ColumnType` | `ColumnType.fromString("VARCHR")`, `""`, or `null` | Throws `InvalidTableModelException` naming invalid value. |
| `TC-005` | Invalid identifiers across all levels | `null`, blank, or dotted name (`"market.data"`) on catalog, namespace, table, or column | Throws matching `Invalid*ModelException`. |
| `TC-006` | Null parent composition | `new NamespaceModel(null, "ns")` or `new TableModel(null, "tbl", cols, null)` | Throws `InvalidNamespaceModelException` / `InvalidTableModelException`. |
| `TC-007` | Empty, null, or duplicate columns & bad partition | Empty list, `null` element, `["id", "ID"]`, or `partitionColumn = "missing"` / `"  "` | Throws `InvalidTableModelException`. |
| `TC-008` | Defensive immutability | Mutate input `List` after constructing `TableModel`; call `.add(...)` on `spec.columns()` | `TableModel` unaffected; direct mutation throws `UnsupportedOperationException`. |
| `TC-009` | Exception hierarchy & dependency hygiene | Construct `*NotFoundException` (valid & `null`), `CatalogOperationException`, `ModelParseException`; run `mvn clean test -pl lakehouse-core` | All extend `LakehouseException`, expose typed models/FQNs, preserve causes, reject `null` models; zero Spark/Iceberg/Jackson imports in `...core.model` or `...core.exception`. |
