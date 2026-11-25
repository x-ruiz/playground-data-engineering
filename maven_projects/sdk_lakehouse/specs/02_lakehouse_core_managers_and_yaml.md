# Feature Spec: `lakehouse-core` Template-Method Managers & YAML Parsing

- **Status:** Approved
- **Module / Component:** `lakehouse-core` (`...core.manager` — Pure Java 17)
- **Archetype:** Java Library Contract
- **Tier:** Mini-Spec
- **Owner:** Xavier Ruiz
- **Depends On:** `specs/00_project_structure_and_organization.md`, `specs/01_lakehouse_core_domain_models.md`

---

## 1. Goal & Context

### Problem Statement
Engine adapter modules (`lakehouse-iceberg`, future Delta/BigLake adapters) must share a uniform GitOps parse-validate-execute lifecycle without duplicating YAML parsing, leaking serialization libraries into callers, or allowing subclasses to bypass validation. Exposing `abstract` Template-Method managers (`TableManager`, `NamespaceManager`) whose `public final` `Path` methods own encapsulated YAML parsing enforces fail-fast validation before any engine hook executes.

### Success Metrics
- `lakehouse-core` compiles and passes all manager and YAML parser tests (`mvn test -pl lakehouse-core`) with zero engine dependencies and zero public parser exposure.
- YAML parsing remains strictly package-private inside `...core.manager`, stateless, and thread-safe.
- Full verification coverage of `TC-001` through `TC-008`.

---

## 2. Scope & Explicit Non-Goals

### In Scope
- Engine-agnostic `public abstract` Template-Method base classes in `...core.manager`:
  - `TableManager`: `public final createTable(Path)` → `protected abstract doCreateTable(TableModel)`.
  - `NamespaceManager`: `public final createNamespace(Path)` → `protected abstract doCreateNamespace(NamespaceModel)`, plus `public final namespaceExists(Path)` & `public final namespaceExists(NamespaceModel)` → `protected abstract doNamespaceExists(NamespaceModel)`.
- Encapsulated package-private stateless YAML parsing for `table.yaml` and `namespace.yaml` inside `...core.manager`, hidden behind the `Path`-based manager methods (extensible to future `.proto` / `.textproto` formats).
- Test fixtures (`table.yaml`, `namespace.yaml`) and in-memory manager test coverage verifying template enforcement and idempotency semantics.

### Non-Goals (Do Not Implement)
- **No public YAML parser API or public `createTable(TableModel)` / `createNamespace(NamespaceModel)` entry points:** Callers interact strictly via `Path` methods (plus `namespaceExists(NamespaceModel)`).
- **No `CatalogManager` or catalog YAML format:** Catalogs are provisioned externally by platform infrastructure—never via declarative YAML and never auto-created.
- **No engine implementations:** `IcebergTableManager` / `IcebergNamespaceManager` live in Spec 03.
- **No Protobuf/gRPC parsing yet:** Deferred to a future spec.
- **No `dropTable`, `tableExists`, `dropNamespace`, `alterTable`, or schema evolution:** Pure idempotent `CREATE IF NOT EXISTS` semantics only.

---

## 3. Functional Requirements

- `REQ-01`: `TableManager` shall be a `public abstract class` exposing `public final void createTable(Path sourcePath)` which parses `sourcePath` into a validated non-null `TableModel` and delegates to `protected abstract void doCreateTable(TableModel)`. Subclass hooks shall never execute if parsing or validation fails.
- `REQ-02`: `TableManager.doCreateTable(TableModel)` contract: subclasses shall throw `CatalogNotFoundException` if the target catalog is missing, throw `NamespaceNotFoundException` if the target namespace is missing (never auto-creating either), and treat an already-existing table as an idempotent no-op.
- `REQ-03`: `NamespaceManager` shall be a `public abstract class` exposing `public final void createNamespace(Path sourcePath)` → `protected abstract void doCreateNamespace(NamespaceModel)` (throwing `CatalogNotFoundException` if catalog is missing; idempotent no-op if namespace already exists) and `public final boolean namespaceExists(Path)` / `public final boolean namespaceExists(NamespaceModel)` → `protected abstract boolean doNamespaceExists(NamespaceModel)` (rejecting `null` `NamespaceModel` with `InvalidNamespaceModelException`).
- `REQ-04`: Internal table YAML parsing shall support `catalog`, `namespace`, `tableName`, `columns` (`name`, `type`, `nullable`), and optional `partitionColumn`, defaulting omitted column `nullable` to `true` and composing `CatalogModel` → `NamespaceModel` → `TableModel`.
- `REQ-05`: Internal namespace YAML parsing shall support `catalog` and `namespace`, composing `CatalogModel` → `NamespaceModel`.
- `REQ-06`: YAML parsing shall fail fast with `ModelParseException` (preserving cause) on `null` `Path`, missing/unreadable file, empty/comment-only file, malformed YAML syntax, or unknown YAML properties (e.g., typo `partitonColumn`).
- `REQ-07`: All parser interfaces, implementations, and Jackson DTOs shall remain package-private or `private` inside `...core.manager`—never `public`, never exposed in `public`/`protected` manager signatures, and never leaking Jackson annotations into `...core.model`.
- `REQ-08`: Internal YAML parsing shall be stateless and thread-safe for concurrent multi-threaded invocation.

---

## 4. Public Java API & Declarative YAML Contract

### Manager Base Classes (`x.ruiz.playground.data.engineering.lakehouse.core.manager`)

| Type | Kind & Constructors | Public / Protected Operations | Contract |
| :--- | :--- | :--- | :--- |
| `TableManager` | `public abstract class`<br/>`protected TableManager()` | `public final void createTable(Path)`<br/>`protected abstract void doCreateTable(TableModel)` | Parses & validates YAML via encapsulated internal parser; invokes `doCreateTable` only with a valid non-null `TableModel`. |
| `NamespaceManager` | `public abstract class`<br/>`protected NamespaceManager()` | `public final void createNamespace(Path)`<br/>`public final boolean namespaceExists(Path)`<br/>`public final boolean namespaceExists(NamespaceModel)`<br/>`protected abstract void doCreateNamespace(NamespaceModel)`<br/>`protected abstract boolean doNamespaceExists(NamespaceModel)` | Parses `Path` inputs and guards against `null` `NamespaceModel` (`InvalidNamespaceModelException`) before delegating to `do*` hooks. |

*(Internal parser abstractions such as `FileModelManager` / `YamlFileModelManager` and any test-injection constructors remain package-private implementation details inside `...core.manager`.)*

### Declarative YAML Formats

**Table YAML (`table.yaml`):**
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

**Namespace YAML (`namespace.yaml`):**
```yaml
catalog: data_lake
namespace: market_data
```

---

## 5. Invariants, Edge Cases & Failure Modes

### Key Invariants
1. **Unbypassable Template Flow:** Because all public entry points are `final`, no engine subclass can skip parsing, validation, or non-null guards; `do*` hooks are never called on parse or semantic failure.
2. **Compile-Time Parser Encapsulation:** External modules cannot import or invoke YAML parsers directly, and zero Jackson types appear in public/protected API signatures.
3. **Thread Safety:** Shared YAML parsing holds zero per-call mutable state.
4. **No Silent Parent Creation:** Missing catalogs always raise `CatalogNotFoundException`; missing namespaces during table creation always raise `NamespaceNotFoundException`.

### Failure Mode Matrix

| Failure Condition | Expected Exception | Notes |
| :--- | :--- | :--- |
| `null` `Path`, missing/unreadable file, empty file, bad YAML syntax, or unknown YAML key | `ModelParseException` | Preserves underlying `IOException`/Jackson cause; `do*` hooks never invoked. |
| Semantically invalid YAML values (bad identifier, bad `ColumnType`, missing `catalog`/`namespace`, invalid `partitionColumn`) | `InvalidCatalogModelException` / `InvalidNamespaceModelException` / `InvalidTableModelException` | Enforced during model construction; `do*` hooks never invoked. |
| `NamespaceManager.namespaceExists((NamespaceModel) null)` | `InvalidNamespaceModelException` | Enforced once by base class; `doNamespaceExists` never invoked. |
| Internal parser returns `null` spec | `InvalidTableModelException` / `InvalidNamespaceModelException` | Guarded by base class; `do*` hooks never invoked. |
| Missing platform catalog / missing namespace | `CatalogNotFoundException` / `NamespaceNotFoundException` | Enforced by subclasses; never auto-creates parents. |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | Valid `table.yaml` happy path | `src/test/resources/table.yaml` | Parses into `TableModel` (`data_lake.market_data.transactions`, 3 typed columns, `partitionColumn == "date"`). |
| `TC-002` | Unpartitioned YAML & default nullability | Table YAML omitting `partitionColumn` and column `nullable` | `isPartitioned() == false` and omitted `nullable` defaults to `true`. |
| `TC-003` | Valid `namespace.yaml` happy path | `src/test/resources/namespace.yaml` | Parses into `NamespaceModel` (`data_lake.market_data`). |
| `TC-004` | Unknown YAML key, malformed, empty, or missing file | YAML with typo `partitonColumn`, broken syntax, empty file, missing `Path`, or `null` `Path` | Throws `ModelParseException` preserving cause where applicable. |
| `TC-005` | Semantic errors in YAML | Invalid identifier, unknown `ColumnType`, or missing `catalog`/`namespace` in YAML | Throws matching `Invalid*ModelException` (not `ModelParseException`). |
| `TC-006` | Template-method enforcement & idempotency (in-memory subclasses) | In-memory `NamespaceManager` and `TableManager` invoked before and after catalog provisioning | Throws `CatalogNotFoundException` when catalog absent; throws `NamespaceNotFoundException` when namespace absent; repeated `createNamespace` and `createTable` succeed idempotently; `namespaceExists` flips `false` → `true`. |
| `TC-007` | Failures never invoke `do*` hooks | `null` `Path`, invalid YAML, `namespaceExists((NamespaceModel) null)`, and stub parser returning `null` | Expected exception thrown and hook invocation count remains `0`. |
| `TC-008` | Concurrent parsing & encapsulation audit | Multi-threaded table YAML parsing + visibility/import inspection | All concurrent threads return equal `TableModel`s with zero errors; parsers are package-private in `...core.manager` and public manager methods are `final`. |
