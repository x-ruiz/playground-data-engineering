# Feature Spec: Repository Folder Structure & Class Organization

- **Status:** Approved
- **Module / Component:** `sdk.lakehouse` (Multi-Module Maven Reactor — Java 17)
- **Archetype:** Software Library Contract (Architecture & Structural Governance)
- **Tier:** Mini-Spec
- **Owner:** Xavier Ruiz

---

## 1. Goal & Context

### Problem Statement
`sdk.lakehouse` is growing from a single contract module (`lakehouse-core`) into a multi-engine lakehouse SDK (`lakehouse-iceberg`, future `lakehouse-delta` / `lakehouse-biglake`, and v2 Protobuf/gRPC ingestion). Currently, all domain specs, service interfaces, and parsers in `lakehouse-core` sit flat in a single package, and there is no canonical reference governing where cross-cutting vs. module-level specs live, how packages split by role, or how engine modules organize helpers and tests. Establishing a foundational `00` structural spec now locks in clean boundaries before building `lakehouse-iceberg` and v2 parsers on top.

### Success Metrics
- Every module, folder, package, and test directory across `sdk.lakehouse` conforms to Section 4 with zero orphaned classes in the root `...lakehouse.core` package.
- `lakehouse-core` compiles and passes all tests with zero engine dependencies (`org.apache.iceberg.*`, `org.apache.spark.*`) and zero serialization leakage into domain specs.
- Every acceptance check (`TC-001` through `TC-008`) passes, including a clean full-reactor `mvn clean test`.

---

## 2. Scope & Explicit Non-Goals

### In Scope
- Canonical multi-module repository folder layout (`specs/`, `plans/`, `lakehouse-core/`, `lakehouse-iceberg/`, future `lakehouse-<engine>/`).
- Spec and plan placement conventions across repository root and child modules.
- Role-based Java subpackage organization inside `lakehouse-core` (`.spec`, `.manager`, `.parser`, `.exception`) and explicit intra-core dependency rules.
- Default flat package organization and subpackage-growth criteria for engine adapter modules (`lakehouse-<engine>`).
- Class visibility, naming conventions, resource/proto layout, and test-package mirroring rules.
- Synchronization requirement for updating dependent module specs (starting with `lakehouse-core/specs/01_lakehouse_core_contracts.md`) upon adoption.

### Non-Goals (Do Not Implement)
- **No JPMS (`module-info.java`) modularization:** Artifacts remain standard Java 17 classpath JARs.
- **No new engine modules or `lakehouse-iceberg` features:** Do NOT scaffold `lakehouse-delta`, `lakehouse-biglake`, or Iceberg managers in this spec.
- **No behavioral contract changes:** Domain validation, idempotency, exception semantics, and parser behavior remain owned by module feature specs (e.g., `lakehouse-core/specs/01_lakehouse_core_contracts.md`).
- **No automated architecture-lint plugins yet:** Adding ArchUnit, Checkstyle, or Spotless to Maven is deferred to a future quality-gates spec.
- **No changes to local dev infrastructure:** `docker-compose.yaml`, `Makefile`, `notebooks/`, and `warehouse/` stay untouched.

---

## 3. Functional Requirements

### Repository & Module Structure
- `REQ-01`: The Maven reactor (`sdk.lakehouse`) shall organize code into one engine-agnostic contract module (`lakehouse-core`) and one sibling module per storage/table engine (`lakehouse-<engine>`, starting with `lakehouse-iceberg`). Engine modules may depend on `lakehouse-core`; `lakehouse-core` shall never depend on any engine module, Apache Iceberg, Apache Spark, or cloud-provider SDK.
- `REQ-02`: Cross-cutting repository and architecture specs (`00_*`) shall reside in root `specs/`; module-specific feature specs shall reside in `<module>/specs/NN_<snake_case>.md`; implementation plans shall reside in root `plans/NN_<snake_case>.md`.

### `lakehouse-core` Package Organization
- `REQ-03`: All production Java types in `lakehouse-core` shall be organized into four role-based subpackages beneath `x.ruiz.playground.data.engineering.lakehouse.core`: `.spec`, `.manager`, `.parser`, and `.exception`. No production classes shall reside directly in `...lakehouse.core`.
- `REQ-04`: `LakehouseSpec` (`sealed interface`) and all of its permitted subtypes (`CatalogSpec`, `NamespaceSpec`, `TableSpec`), together with leaf types `ColumnSpec` and `ColumnType`, shall reside in `...lakehouse.core.spec` (satisfying Java 17 unnamed-module `sealed` co-location rules).
- `REQ-05`: `...lakehouse.core.manager` shall contain only engine-agnostic service port interfaces (`NamespaceManager`, `TableManager`) and any future engine-agnostic port contracts—never concrete engine implementations.
- `REQ-06`: `...lakehouse.core.parser` shall contain `FileParser<T extends LakehouseSpec>` and format-specific parser utilities/adapters (`YamlSpecParsers`, future `ProtoSpecParsers`). Serialization DTOs and third-party serialization annotations (Jackson, Protobuf) shall be confined strictly to `...lakehouse.core.parser`.
- `REQ-07`: `...lakehouse.core.exception` shall contain `LakehouseException` and all domain, parsing, not-found, and operation exception subclasses.
- `REQ-08`: Intra-core package dependencies shall obey strict layering:
  - `core.spec` and `core.exception` form the core foundation layer (specs throw `Invalid*SpecException`; `*NotFoundException` carries typed `*Spec` references) and shall never import `core.manager` or `core.parser`.
  - `core.manager` may depend only on `core.spec` and `core.exception` (never `core.parser`).
  - `core.parser` may depend only on `core.spec` and `core.exception` (never `core.manager`).

### Engine Modules, Visibility, Naming & Tests
- `REQ-09`: Engine adapter modules (`lakehouse-<engine>`) shall keep classes flat under `x.ruiz.playground.data.engineering.lakehouse.<engine>` by default, using package-private visibility for internal helpers (e.g., `IcebergTypeMapper`). Engine subpackages (e.g., `.catalog`, `.auth`) shall only be introduced when a distinct cohesive subsystem grows to 3+ related classes or requires an explicit boundary.
- `REQ-10`: Internal translation helpers, mapper utilities, and serialization DTOs shall default to package-private or `private` nested types; only deliberate SDK entry points (specs, managers, exceptions, public parser APIs, and public engine adapters) shall be `public`.
- `REQ-11`: Classes, resources, and tests shall follow uniform placement and naming conventions:
  - Protobuf schemas under `lakehouse-core/src/main/proto/*.proto`; runtime configs under `<module>/src/main/resources/`; test fixtures under `<module>/src/test/resources/`.
  - Unit tests under `<module>/src/test/java` shall mirror the exact package of the production types under test and use the `*Test` suffix (future integration tests shall use `*IT`).
- `REQ-12`: Any package restructuring performed to comply with this spec shall update affected module specs (starting with `lakehouse-core/specs/01_lakehouse_core_contracts.md`) in the same change so specs remain accurate.

---

## 4. Structural & Package Contract

### Repository Folder Layout

```text
sdk_lakehouse/
├── pom.xml                                  # Parent reactor (dependencyManagement, Java 17)
├── Makefile
├── docker-compose.yaml                      # Local Iceberg REST + MinIO + Spark sandbox
├── README.md
├── plans/
│   └── 01_<plan_name>.md                    # Sequential implementation plans
├── specs/
│   └── 00_project_structure_and_organization.md  # Cross-cutting architecture specs
├── lakehouse-core/                          # Pure Java 17 engine-agnostic contracts
│   ├── pom.xml
│   ├── specs/
│   │   └── 01_lakehouse_core_contracts.md   # Module-level feature specs
│   └── src/
│       ├── main/
│       │   ├── java/x/ruiz/playground/data/engineering/lakehouse/core/
│       │   │   ├── spec/                    # Immutable domain specs & canonical ColumnType
│       │   │   ├── manager/                 # Engine-agnostic service port interfaces
│       │   │   ├── parser/                  # FileParser<T> + format parser utilities (YAML, Proto)
│       │   │   └── exception/               # LakehouseException hierarchy
│       │   ├── proto/                       # .proto wire/file schemas
│       │   └── resources/                   # log4j2.xml and runtime config
│       └── test/
│           ├── java/x/ruiz/playground/data/engineering/lakehouse/core/
│           │   ├── spec/                    # Spec & type unit tests
│           │   ├── manager/                 # Service contract & exception tests
│           │   └── parser/                  # Parser unit & concurrency tests
│           └── resources/                   # Fixture files (table.yaml, namespace.yaml, table.textproto)
└── lakehouse-iceberg/                       # Apache Iceberg adapter module
    ├── pom.xml
    ├── specs/                               # Iceberg module feature specs (starting at 01_ or 02_)
    └── src/
        ├── main/java/x/ruiz/playground/data/engineering/lakehouse/iceberg/
        │   └── ...                          # Flat by default; package-private mappers/helpers
        └── test/
            ├── java/x/ruiz/playground/data/engineering/lakehouse/iceberg/
            └── resources/
```

### Module Inventory & Dependency Rules

| Module | Artifact ID | Responsibility | Allowed Internal Deps | Forbidden Dependencies |
| :--- | :--- | :--- | :--- | :--- |
| Root Reactor | `sdk.lakehouse` (`pom`) | Centralizes Maven `dependencyManagement`, `pluginManagement`, and Java 17 release config. | Declares child modules only. | N/A (`pom` packaging). |
| Core Contracts | `lakehouse-core` (`jar`) | Immutable domain specs, service ports, file/wire parsers, and custom exceptions. | None (leaf module). | `lakehouse-*` engine modules, `org.apache.iceberg.*`, `org.apache.spark.*`, AWS/GCP/Azure SDKs. |
| Iceberg Adapter | `lakehouse-iceberg` (`jar`) | Implements `lakehouse-core` managers against Apache Iceberg Catalogs + type/partition mapping. | `lakehouse-core`. | Other engine modules (`lakehouse-delta`, etc.). |
| Future Engine Adapters | `lakehouse-<engine>` (`jar`) | Implements `lakehouse-core` managers for another table format or catalog engine. | `lakehouse-core`. | Sibling engine modules (adapters never cross-depend). |

### `lakehouse-core` Package Map (`x.ruiz.playground.data.engineering.lakehouse.core.*`)

| Package | Role | Member Types | Allowed Internal Imports | Forbidden Imports |
| :--- | :--- | :--- | :--- | :--- |
| `...core.spec` | Immutable domain value models & canonical type enum | `LakehouseSpec`, `CatalogSpec`, `NamespaceSpec`, `TableSpec`, `ColumnSpec`, `ColumnType` | `...core.exception` (validation exceptions) | `...core.manager`, `...core.parser`, Jackson/Protobuf/engine libs |
| `...core.manager` | Engine-agnostic service port interfaces | `NamespaceManager`, `TableManager` | `...core.spec`, `...core.exception` | `...core.parser`, Jackson/Protobuf/engine libs |
| `...core.parser` | Generic file-parser contract & format parsers | `FileParser`, `YamlSpecParsers` *(future: `ProtoSpecParsers`)* | `...core.spec`, `...core.exception` | `...core.manager`, engine libs |
| `...core.exception` | Unchecked exception hierarchy | `LakehouseException`, `InvalidCatalogSpecException`, `InvalidNamespaceSpecException`, `InvalidTableSpecException`, `SpecParseException`, `CatalogNotFoundException`, `NamespaceNotFoundException`, `CatalogOperationException` | `...core.spec` (for `*NotFoundException` metadata) | `...core.manager`, `...core.parser`, Jackson/Protobuf/engine libs |

### Engine Module Package Strategy (`x.ruiz.playground.data.engineering.lakehouse.<engine>`)

| Level | Package Pattern | When to Use | Visibility Guidance |
| :--- | :--- | :--- | :--- |
| Default (Flat) | `...lakehouse.<engine>` | Standard starting layout for engine managers, mappers, and entry points (e.g., `IcebergTableManager`, `IcebergNamespaceManager`, `IcebergTypeMapper`). | Public managers & factories; package-private mappers/translators so tests in the same package can unit-test them directly without widening the public API. |
| Subsystem Split | `...lakehouse.<engine>.<subsystem>` | Only when a cohesive subsystem grows to 3+ related classes or owns an independent concern (e.g., `.catalog` for REST/Glue/Hive factories, `.auth` for credential providers). | Expose only the subsystem's facade/factory as `public`; keep internal builders/helpers package-private. |

### Naming & Visibility Conventions

| Category | Suffix / Pattern | Modifier / Kind | Example |
| :--- | :--- | :--- | :--- |
| Domain specs | `*Spec` | `public record` (or `public sealed interface` root) | `CatalogSpec`, `NamespaceSpec`, `TableSpec`, `ColumnSpec`, `LakehouseSpec` |
| Canonical enums | Domain noun | `public enum` | `ColumnType` |
| Service port interfaces | `*Manager` | `public interface` | `NamespaceManager`, `TableManager` |
| Engine port implementations | `<Engine>*Manager` | `public class` (or `final class`) | `IcebergTableManager`, `IcebergNamespaceManager` |
| Parser contract | `FileParser<T>` | `public @FunctionalInterface` | `FileParser<TableSpec>` |
| Stateless format parser utilities | `<Format>SpecParsers` | `public final class` (private ctor, `static` methods) | `YamlSpecParsers`, `ProtoSpecParsers` |
| Internal mappers / DTOs | `<Engine>*Mapper`, `<Format>*Dto` | Package-private `final class` or `private record` | `IcebergTypeMapper`, `YamlTableDto` |
| Domain & runtime exceptions | `*Exception` | `public class extends LakehouseException` | `CatalogNotFoundException`, `SpecParseException` |
| Unit tests / Integration tests | `*Test` / `*IT` | Package-private JUnit 5 test class in mirrored package | `TableSpecTest`, `YamlSpecParsersTest`, `IcebergTableManagerIT` |

---

## 5. Invariants, Edge Cases & Failure Modes

### Key Invariants
1. **Engine Independence of `lakehouse-core`:** Zero references to Iceberg, Spark, or cloud storage SDKs in `lakehouse-core/pom.xml` or `lakehouse-core/src/`.
2. **Sealed Hierarchy Integrity:** Every `permits` member of `LakehouseSpec` lives in `...core.spec`; adding a new `LakehouseSpec` subtype in another package is impossible in an unnamed Java 17 module.
3. **Zero Serialization Leakage:** Jackson (`@Json*`, `ObjectMapper`, `YAMLMapper`) and future Protobuf types appear exclusively inside `...core.parser`—never in `...core.spec`, `...core.manager`, or `...core.exception`.
4. **Manager / Parser Orthogonality:** `...core.manager` never imports `...core.parser`, and `...core.parser` never imports `...core.manager`. Callers (CLI, gRPC handler, GitOps runner) compose parsers and managers at the application boundary.
5. **Single Source of Truth for Specs:** Root `specs/` holds only cross-cutting architecture specs (`00_*`); module feature specs live inside their owning module's `specs/` folder and must always match actual package paths.
6. **Mirrored Test Packages:** Unit tests live in the same package as the class under test so package-private helpers remain encapsulated while still directly testable.

### Failure Mode Matrix

| Structural Violation / Edge Case | Expected Detection / Failure | Remediation |
| :--- | :--- | :--- |
| A `LakehouseSpec` subtype is placed outside `...core.spec` | Java 17 compile error (`class is not allowed to extend sealed class from another package`) | Move subtype into `...core.spec`. |
| Production class placed directly in root `...lakehouse.core` | Code review / directory audit failure (`TC-002`) | Move into `.spec`, `.manager`, `.parser`, or `.exception`. |
| Serialization annotation or `YAMLMapper` added to `...core.spec` | Code review / grep audit failure (`TC-005`) | Move serialization concern to a private DTO inside `...core.parser`. |
| `...core.manager` imports `...core.parser` (or vice versa) | Code review / import audit failure (`TC-004`) | Decouple; wire parser output into manager at caller layer. |
| Engine dependency added to `lakehouse-core/pom.xml` | Code review / POM audit failure (`TC-004`) | Move engine dependency into the target `lakehouse-<engine>/pom.xml`. |
| Downstream module (`lakehouse-iceberg`) has stale imports after package refactor | Maven reactor compile failure (`TC-007`, `TC-008`) | Update imports in downstream modules and verify full-reactor `mvn clean test`. |
| Module spec (`01_lakehouse_core_contracts.md`) still documents old flat package paths | Spec review drift failure (`TC-007`) | Update affected spec package tables in the same change. |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | Spec & plan directory hierarchy | Inspect repo root and child modules | Cross-cutting spec exists at `specs/00_project_structure_and_organization.md`, core feature spec at `lakehouse-core/specs/01_lakehouse_core_contracts.md`, and plans under root `plans/`. |
| `TC-002` | Zero root-package stragglers in `lakehouse-core` | List all `.java` files under `lakehouse-core/src/main/java` | Zero `.java` files exist directly in `...lakehouse.core`; every production class resides in `.spec`, `.manager`, `.parser`, or `.exception`. |
| `TC-003` | Sealed hierarchy co-location | Inspect `...lakehouse.core.spec` and compile on Java 17 | `LakehouseSpec`, `CatalogSpec`, `NamespaceSpec`, `TableSpec`, `ColumnSpec`, and `ColumnType` all reside in `...lakehouse.core.spec` and compile cleanly. |
| `TC-004` | Package & module dependency hygiene | Audit imports across `lakehouse-core/src/main/java` and `lakehouse-core/pom.xml` | Zero Iceberg/Spark dependencies in `lakehouse-core`; `core.spec` and `core.exception` never import `core.manager` or `core.parser`; `core.manager` and `core.parser` never import each other. |
| `TC-005` | Encapsulation & serialization isolation | Audit Jackson references and class modifiers in `lakehouse-core` | `YamlSpecParsers` is `final` with a `private` constructor and `private` DTO records; zero Jackson imports or `@Json*` annotations exist outside `...core.parser`. |
| `TC-006` | Mirrored test packages | Inspect `lakehouse-core/src/test/java` | Every `*Test.java` class resides in the subpackage matching its production subject (`.spec`, `.parser`, `.manager` / `.exception`). |
| `TC-007` | Downstream & Spec 01 synchronization | Inspect `lakehouse-iceberg/src` and `lakehouse-core/specs/01_lakehouse_core_contracts.md` | `lakehouse-iceberg` compiles against the new `...core.*` subpackages, and Spec 01 documents the exact updated subpackage paths. |
| `TC-008` | Full multi-module reactor verification | Run `mvn clean test` at repository root | Both `lakehouse-core` and `lakehouse-iceberg` compile cleanly on Java 17 and all unit tests pass with 0 failures and 0 errors. |
