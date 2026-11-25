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
- Role-based Java subpackage organization inside `lakehouse-core` (`.model`, `.manager`, `.exception`) with package-private parser encapsulation inside `.manager` and explicit intra-core dependency rules.
- Default flat package organization and subpackage-growth criteria for engine adapter modules (`lakehouse-<engine>`).
- Class visibility, naming conventions, resource/proto layout, and test-package mirroring rules.
- Synchronization requirement for updating dependent specs in root `specs/` upon structural adoption.

### Non-Goals (Do Not Implement)
- **No JPMS (`module-info.java`) modularization:** Artifacts remain standard Java 17 classpath JARs.
- **No new engine modules or `lakehouse-iceberg` features:** Do NOT scaffold `lakehouse-delta`, `lakehouse-biglake`, or Iceberg managers in this spec.
- **No behavioral contract changes:** Domain validation, idempotency, exception semantics, and parser behavior remain owned by feature specs (`specs/01_*.md` onward).
- **No automated architecture-lint plugins yet:** Adding ArchUnit, Checkstyle, or Spotless to Maven is deferred to a future quality-gates spec.
- **No changes to local dev infrastructure:** `docker-compose.yaml`, `Makefile`, `notebooks/`, and `warehouse/` stay untouched.

---

## 3. Functional Requirements

### Repository & Module Structure
- `REQ-01`: The Maven reactor (`sdk.lakehouse`) shall organize code into one engine-agnostic contract module (`lakehouse-core`), one cloud-agnostic sibling module per storage/table engine (`lakehouse-<engine>`, starting with `lakehouse-iceberg`), and optional thin cloud-plugin modules (`lakehouse-<engine>-<cloud>`, such as `lakehouse-iceberg-aws` and future `lakehouse-iceberg-gcp`) discovered via Java `ServiceLoader` SPI. Engine modules depend on `lakehouse-core` and remain free of cloud-specific SDKs; cloud-plugin modules depend on their parent engine module (`lakehouse-<engine>`); `lakehouse-core` shall never depend on any engine module, Apache Iceberg, Apache Spark, or cloud-provider SDK.
- `REQ-02`: All repository and feature specs shall reside in root `specs/NN_<snake_case>.md` (starting with `00_` for repository structure, followed by focused single-deliverable feature specs `01_`–`05_`); implementation plans shall reside in root `plans/NN_<snake_case>.md`.

### `lakehouse-core` Package Organization
- `REQ-03`: All production Java types in `lakehouse-core` shall be organized into three role-based subpackages beneath `x.ruiz.playground.data.engineering.lakehouse.core`: `.model`, `.manager`, and `.exception`. No production classes shall reside directly in `...lakehouse.core`.
- `REQ-04`: `LakehouseModel` (`sealed interface`) and all of its permitted subtypes (`CatalogModel`, `NamespaceModel`, `TableModel`), together with leaf types `ColumnModel` and `ColumnType`, shall reside in `...lakehouse.core.model` (satisfying Java 17 unnamed-module `sealed` co-location rules).
- `REQ-05`: `...lakehouse.core.manager` shall contain the `public abstract` Template-Method manager base classes (`NamespaceManager`, `TableManager`) and their **package-private** internal file/wire parsers (`FileModelManager`, `YamlFileModelManager`, future `ProtoFileModelManager`). Concrete engine implementations shall never reside in `lakehouse-core`.
- `REQ-06`: File parsers, serialization DTOs, and third-party serialization annotations/APIs (Jackson, Protobuf) shall remain package-private or `private` inside `...lakehouse.core.manager`—never exposed publicly to calling libraries and never leaking into `...lakehouse.core.model` or `...lakehouse.core.exception`.
- `REQ-07`: `...lakehouse.core.exception` shall contain `LakehouseException` and all domain, parsing, not-found, and operation exception subclasses.
- `REQ-08`: Intra-core package dependencies shall obey strict layering:
  - `core.model` and `core.exception` form the core foundation layer (models throw `Invalid*ModelException`; `*NotFoundException` carries typed `*Model` references) and shall never import `core.manager`.
  - `core.manager` may depend only on `core.model` and `core.exception` (plus internal serialization libraries confined to its package-private parser classes).

### Engine Modules, Visibility, Naming & Tests
- `REQ-09`: Engine adapter modules (`lakehouse-<engine>`) shall keep classes flat under `x.ruiz.playground.data.engineering.lakehouse.<engine>` by default, using package-private visibility for internal helpers (e.g., `IcebergTypeMapper`). Engine subpackages (e.g., `.catalog`, `.auth`) shall only be introduced when a distinct cohesive subsystem grows to 3+ related classes or requires an explicit boundary.
- `REQ-10`: Internal file parsers, translation helpers, mapper utilities, and serialization DTOs shall default to package-private or `private` nested types; only deliberate SDK entry points (models, managers, exceptions, and public engine adapters) shall be `public`.
- `REQ-11`: Classes, resources, and tests shall follow uniform placement and naming conventions:
  - Protobuf schemas under `lakehouse-core/src/main/proto/*.proto`; runtime configs under `<module>/src/main/resources/`; test fixtures under `<module>/src/test/resources/`.
  - Unit tests under `<module>/src/test/java` shall mirror the exact package of the production types under test and use the `*Test` suffix (future integration tests shall use `*IT`).
- `REQ-12`: Any package restructuring performed to comply with this spec shall update affected specs under root `specs/` in the same change so specs remain accurate.

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
│   ├── 00_project_structure_and_organization.md  # Cross-cutting architecture spec
│   ├── 01_lakehouse_core_domain_models.md        # Core immutable domain models & exceptions
│   ├── 02_lakehouse_core_managers_and_yaml.md    # Template-Method managers & YAML parsing
│   ├── 03_iceberg_core_adapter_and_spi.md        # Cloud-agnostic Iceberg adapter & catalog SPI
│   ├── 04_iceberg_aws_plugin.md                  # AWS / MinIO SPI plugin module
│   └── 05_iceberg_gcp_biglake_plugin.md          # GCP BigLake SPI plugin + Iceberg 1.10.1 upgrade
├── lakehouse-core/                          # Pure Java 17 engine-agnostic contracts
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/x/ruiz/playground/data/engineering/lakehouse/core/
│       │   │   ├── model/                    # Immutable domain specs & canonical ColumnType
│       │   │   ├── manager/                 # Abstract manager base classes + package-private parsers
│       │   │   └── exception/               # LakehouseException hierarchy
│       │   ├── proto/                       # .proto wire/file schemas
│       │   └── resources/                   # log4j2.xml and runtime config
│       └── test/
│           ├── java/x/ruiz/playground/data/engineering/lakehouse/core/
│           │   ├── model/                    # Spec & type unit tests
│           │   └── manager/                 # Manager template, parser & exception unit tests
│           └── resources/                   # Fixture files (table.yaml, namespace.yaml, table.textproto)
├── lakehouse-iceberg/                       # Cloud-agnostic Apache Iceberg adapter module
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/x/ruiz/playground/data/engineering/lakehouse/iceberg/
│       │   │   ├── ...                      # Flat managers & package-private mappers
│       │   │   └── catalog/                 # Catalog SPI & built-in providers (3+ classes)
│       │   └── resources/META-INF/services/ # ServiceLoader provider registrations
│       └── test/
│           ├── java/x/ruiz/playground/data/engineering/lakehouse/iceberg/
│           └── resources/
├── lakehouse-iceberg-aws/                   # Thin AWS / MinIO SPI plugin module (zero GCP deps)
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/x/ruiz/playground/data/engineering/lakehouse/iceberg/aws/
│       │   └── resources/META-INF/services/ # AWS / MinIO ServiceLoader registrations
│       └── test/
│           ├── java/x/ruiz/playground/data/engineering/lakehouse/iceberg/aws/
│           └── resources/
└── lakehouse-iceberg-gcp/                   # Thin GCP BigLake SPI plugin module (zero AWS deps)
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/x/ruiz/playground/data/engineering/lakehouse/iceberg/gcp/
        │   └── resources/META-INF/services/ # GCP BigLake ServiceLoader registrations
        └── test/
            ├── java/x/ruiz/playground/data/engineering/lakehouse/iceberg/gcp/
            └── resources/
```

### Module Inventory & Dependency Rules

| Module | Artifact ID | Responsibility | Allowed Internal Deps | Forbidden Dependencies |
| :--- | :--- | :--- | :--- | :--- |
| Root Reactor | `sdk.lakehouse` (`pom`) | Centralizes Maven `dependencyManagement`, `pluginManagement`, and Java 17 release config. | Declares child modules only. | N/A (`pom` packaging). |
| Core Contracts | `lakehouse-core` (`jar`) | Immutable domain models, Template-Method managers, package-private file model managers, and custom exceptions. | None (leaf module). | `lakehouse-*` engine modules, `org.apache.iceberg.*`, `org.apache.spark.*`, AWS/GCP/Azure SDKs. |
| Iceberg Adapter | `lakehouse-iceberg` (`jar`) | Cloud-agnostic Iceberg managers, type/partition mapping, and `IcebergCatalogProvider` SPI + built-in providers (`in_memory`, `generic`). | `lakehouse-core`. | Cloud SDKs (`iceberg-aws`, `iceberg-gcp`), other engine modules (`lakehouse-delta`, etc.). |
| Iceberg Cloud Plugins | `lakehouse-iceberg-<cloud>` (`jar`) | Thin SPI plugin JARs (e.g., `lakehouse-iceberg-aws`, future `lakehouse-iceberg-gcp`) bundling only that cloud's `FileIO` and catalog provider implementations. | `lakehouse-iceberg` (transitively `lakehouse-core`). | Sibling cloud plugins (AWS plugin never depends on GCP plugin or vice versa). |
| Future Engine Adapters | `lakehouse-<engine>` (`jar`) | Implements `lakehouse-core` managers for another table format or catalog engine. | `lakehouse-core`. | Sibling engine modules (adapters never cross-depend). |

### `lakehouse-core` Package Map (`x.ruiz.playground.data.engineering.lakehouse.core.*`)

| Package | Role | Member Types | Allowed Internal Imports | Forbidden Imports |
| :--- | :--- | :--- | :--- | :--- |
| `...core.model` | Immutable domain value models & canonical type enum | `LakehouseModel`, `CatalogModel`, `NamespaceModel`, `TableModel`, `ColumnModel`, `ColumnType` | `...core.exception` (validation exceptions) | `...core.manager`, Jackson/Protobuf/engine libs |
| `...core.manager` | Engine-agnostic Template-Method managers + package-private internal parsers | `public`: `NamespaceManager`, `TableManager`<br/>*(package-private)*: `FileModelManager`, `YamlFileModelManager` *(future: `ProtoFileModelManager`)* | `...core.model`, `...core.exception` | Engine libs (`org.apache.iceberg.*`, `org.apache.spark.*`) |
| `...core.exception` | Unchecked exception hierarchy | `LakehouseException`, `InvalidCatalogModelException`, `InvalidNamespaceModelException`, `InvalidTableModelException`, `ModelParseException`, `CatalogNotFoundException`, `NamespaceNotFoundException`, `CatalogOperationException` | `...core.model` (for `*NotFoundException` metadata) | `...core.manager`, Jackson/Protobuf/engine libs |

### Engine Module Package Strategy (`x.ruiz.playground.data.engineering.lakehouse.<engine>`)

| Level | Package Pattern | When to Use | Visibility Guidance |
| :--- | :--- | :--- | :--- |
| Default (Flat) | `...lakehouse.<engine>` | Standard starting layout for engine managers, mappers, and entry points (e.g., `IcebergTableManager`, `IcebergNamespaceManager`, `IcebergTypeMapper`). | Public managers & factories; package-private mappers/translators so tests in the same package can unit-test them directly without widening the public API. |
| Subsystem Split | `...lakehouse.<engine>.<subsystem>` | Only when a cohesive subsystem grows to 3+ related classes or owns an independent concern (e.g., `.catalog` for REST/Glue/Hive factories, `.auth` for credential providers). | Expose only the subsystem's facade/factory as `public`; keep internal builders/helpers package-private. |

### Naming & Visibility Conventions

| Category | Suffix / Pattern | Modifier / Kind | Example |
| :--- | :--- | :--- | :--- |
| Domain specs | `*Model` | `public record` (or `public sealed interface` root) | `CatalogModel`, `NamespaceModel`, `TableModel`, `ColumnModel`, `LakehouseModel` |
| Canonical enums | Domain noun | `public enum` | `ColumnType` |
| Manager base classes | `*Manager` | `public abstract class` (`public final` template methods + `protected abstract` `do*` hooks) | `NamespaceManager`, `TableManager` |
| Engine manager implementations | `<Engine>*Manager` | `public class extends *Manager` (or `final class`) | `IcebergTableManager`, `IcebergNamespaceManager` |
| Internal parser contract | `FileModelManager` | *(package-private)* `interface` in `...core.manager` | `FileModelManager` |
| Internal format parser implementations | `<Format>FileModelManager` | *(package-private)* `final class implements FileModelManager` in `...core.manager` | `YamlFileModelManager`, `ProtoFileModelManager` |
| Internal mappers / DTOs | `<Engine>*Mapper`, `<Format>*Dto` | Package-private `final class` or `private record` | `IcebergTypeMapper`, `YamlTableDto` |
| Domain & runtime exceptions | `*Exception` | `public class extends LakehouseException` | `CatalogNotFoundException`, `ModelParseException` |
| Unit tests / Integration tests | `*Test` / `*IT` | Package-private JUnit 5 test class in mirrored package | `TableModelTest`, `YamlFileModelManagerTest`, `IcebergTableManagerIT` |

---

## 5. Invariants, Edge Cases & Failure Modes

### Key Invariants
1. **Engine Independence of `lakehouse-core`:** Zero references to Iceberg, Spark, or cloud storage SDKs in `lakehouse-core/pom.xml` or `lakehouse-core/src/`.
2. **Sealed Hierarchy Integrity:** Every `permits` member of `LakehouseModel` lives in `...core.model`; adding a new `LakehouseModel` subtype in another package is impossible in an unnamed Java 17 module.
3. **Zero Serialization Leakage:** Jackson (`@Json*`, `ObjectMapper`, `YAMLMapper`) and future Protobuf types appear exclusively inside package-private parser classes in `...core.manager`—never in `...core.model`, `...core.exception`, or `public`/`protected` manager signatures.
4. **Compile-Time Parser Encapsulation:** `FileModelManager` and `YamlFileModelManager` are package-private inside `...core.manager`, guaranteeing that external libraries and engine modules cannot import or invoke YAML parsers directly (on either classpath or module-path runtimes).
5. **Single Source of Truth for Specs:** All cross-cutting (`00_*`) and feature (`01_*`+) specs reside in root `specs/` and must always match actual package paths.
6. **Mirrored Test Packages:** Unit tests live in the same package as the class under test so package-private helpers remain encapsulated while still directly testable.

### Failure Mode Matrix

| Structural Violation / Edge Case | Expected Detection / Failure | Remediation |
| :--- | :--- | :--- |
| A `LakehouseModel` subtype is placed outside `...core.model` | Java 17 compile error (`class is not allowed to extend sealed class from another package`) | Move subtype into `...core.model`. |
| Production class placed directly in root `...lakehouse.core` | Code review / directory audit failure (`TC-002`) | Move into `.model`, `.manager`, or `.exception`. |
| Serialization annotation or `YAMLMapper` added to `...core.model` or `...core.exception` | Code review / grep audit failure (`TC-005`) | Confine serialization concerns to private DTOs inside package-private parser classes in `...core.manager`. |
| `YamlFileModelManager` or `FileModelManager` declared `public` (or leaked in a `public`/`protected` manager signature) | Code review / visibility audit failure (`TC-005`) | Keep parser types package-private inside `...core.manager` and expose only `Path`-based template methods publicly. |
| Engine dependency added to `lakehouse-core/pom.xml` | Code review / POM audit failure (`TC-004`) | Move engine dependency into the target `lakehouse-<engine>/pom.xml`. |
| Downstream module (`lakehouse-iceberg`) has stale imports after package refactor | Maven reactor compile failure (`TC-007`, `TC-008`) | Update imports in downstream modules and verify full-reactor `mvn clean test`. |
| Root `specs/*.md` still documents old flat package paths | Spec review drift failure (`TC-007`) | Update affected spec package tables in the same change. |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | Spec & plan directory hierarchy | Inspect repo root and child modules | All specs (`00_*` through `05_*`) reside under root `specs/` and plans under root `plans/`. |
| `TC-002` | Zero root-package stragglers in `lakehouse-core` | List all `.java` files under `lakehouse-core/src/main/java` | Zero `.java` files exist directly in `...lakehouse.core`; every production class resides in `.model`, `.manager`, or `.exception`. |
| `TC-003` | Sealed hierarchy co-location | Inspect `...lakehouse.core.model` and compile on Java 17 | `LakehouseModel`, `CatalogModel`, `NamespaceModel`, `TableModel`, `ColumnModel`, and `ColumnType` all reside in `...lakehouse.core.model` and compile cleanly. |
| `TC-004` | Package & module dependency hygiene | Audit imports across `lakehouse-core/src/main/java` and `lakehouse-core/pom.xml` | Zero Iceberg/Spark dependencies in `lakehouse-core`; `core.model` and `core.exception` never import `core.manager`. |
| `TC-005` | Encapsulation & serialization isolation | Audit Jackson references and class modifiers in `lakehouse-core` | `FileModelManager` and `YamlFileModelManager` are package-private in `...core.manager` (`YamlFileModelManager` is `final` with private DTO records); zero Jackson imports or `@Json*` annotations exist in `...core.model`, `...core.exception`, or `TableManager`/`NamespaceManager`. |
| `TC-006` | Mirrored test packages | Inspect `lakehouse-core/src/test/java` | Every `*Test.java` class resides in the subpackage matching its production subject (`.model` or `.manager`). |
| `TC-007` | Downstream & feature spec synchronization | Inspect `lakehouse-iceberg/src` and root `specs/` | Downstream modules compile against the `...core.*` subpackages, and feature specs document the exact current package paths. |
| `TC-008` | Full multi-module reactor verification | Run `mvn clean test` at repository root | Both `lakehouse-core` and `lakehouse-iceberg` compile cleanly on Java 17 and all unit tests pass with 0 failures and 0 errors. |
