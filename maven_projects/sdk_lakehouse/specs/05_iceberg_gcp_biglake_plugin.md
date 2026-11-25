# Feature Spec: `lakehouse-iceberg-gcp` BigLake REST SPI Plugin & Iceberg `1.10.1` Upgrade

- **Status:** Approved
- **Module / Component:** `lakehouse-iceberg-gcp` (Thin GCP BigLake SPI Plugin) + Centralized Iceberg `1.10.1` Property — Java 17
- **Archetype:** Java Library Contract (Cloud SPI Plugin)
- **Tier:** Mini-Spec
- **Owner:** Xavier Ruiz
- **Depends On:** `specs/00_project_structure_and_organization.md`, `specs/03_iceberg_core_adapter_and_spi.md`, `specs/04_iceberg_aws_plugin.md`

---

## 1. Goal & Context

### Problem Statement
Specs 03 and 04 established the cloud-agnostic `lakehouse-iceberg` adapter and the thin `lakehouse-iceberg-aws` plugin. To run the identical Airflow/Argo/Spark GitOps pipeline on Google Cloud BigLake Iceberg REST without dragging AWS SDKs into GCP runtime JARs, we need a sibling `lakehouse-iceberg-gcp` plugin backed by `iceberg-gcp` (`GCSFileIO` + native `GoogleAuthManager`). Because `org.apache.iceberg.gcp.auth.GoogleAuthManager` was introduced in Apache Iceberg **`1.10.0`**, this spec also upgrades the centralized `<iceberg.version>` property to **`1.10.1`** cleanly across the reactor.

### Success Metrics
- All 4 Maven modules (`lakehouse-core`, `lakehouse-iceberg`, `lakehouse-iceberg-aws`, `lakehouse-iceberg-gcp`) compile and pass `mvn clean test` on Java 17 and Apache Iceberg `1.10.1` with zero GCP credentials or Docker required.
- `lakehouse-iceberg-gcp` discovers `["gcp_biglake", "generic", "in_memory"]` via `ServiceLoader` with **zero** AWS SDK / `iceberg-aws` dependencies.
- Full verification coverage of `TC-001` through `TC-009` (including strict Javadoc `-Ddoclint=all` and opt-in `BigLakeRestCatalogIT`).

---

## 2. Scope & Explicit Non-Goals

### In Scope
- Centralized Iceberg `1.10.1` upgrade via `<iceberg.version>` in `sdk_lakehouse/pom.xml` (inheritable from `parent.iceberg`), managing `lakehouse-iceberg-gcp`, `iceberg-gcp`, and `iceberg-gcp-bundle` in `<dependencyManagement>`.
- Thin `lakehouse-iceberg-gcp` SPI plugin module (zero `iceberg-aws` dependencies; `maven-shade-plugin` with `ServicesResourceTransformer`):
  - `x.ruiz.playground.data.engineering.lakehouse.iceberg.gcp.GcpBigLakeCatalogProvider` (backend `"gcp_biglake"`).
  - `META-INF/services/...IcebergCatalogProvider` registration.
- Deterministic unit tests (`GcpBigLakeCatalogProviderTest`) and opt-in live BigLake integration test (`BigLakeRestCatalogIT`, `@Tag("integration")`).

### Non-Goals (Do Not Implement)
- **No modifications to `lakehouse-core` or `lakehouse-iceberg` managers:** Existing managers operate unchanged against BigLake's `RESTCatalog`.
- **No custom GCP OAuth token HTTP code:** Delegates auth and token refresh to Iceberg `1.10.1`'s native `org.apache.iceberg.gcp.auth.GoogleAuthManager` (ADC or `gcp.auth.credentials-path`).
- **No GCP infrastructure provisioning (OpenTofu / `gcloud`):** BigLake catalogs, GCS buckets, and IAM roles remain platform-provisioned externally.
- **No legacy BigQuery Metastore Thrift/Hadoop catalog:** Strictly Apache Iceberg REST Catalog (`https://biglake.googleapis.com/iceberg/v1/restcatalog`).

---

## 3. Functional Requirements

- `REQ-01`: Upgrade Iceberg from `1.7.2` to **`1.10.1`** via the single `<iceberg.version>` Maven property with zero hardcoded Iceberg versions in child module POMs.
- `REQ-02`: Register `lakehouse-iceberg-gcp` in `sdk_lakehouse/pom.xml` (`<modules>` + `<dependencyManagement>`); `lakehouse-iceberg-gcp/pom.xml` shall depend on `lakehouse-iceberg`, `iceberg-gcp`, and `iceberg-gcp-bundle`, contain zero AWS dependencies, and configure `ServicesResourceTransformer`.
- `REQ-03`: `GcpBigLakeCatalogProvider` shall be a `public final class` implementing `IcebergCatalogProvider` (`public` no-arg ctor, `backendType() == "gcp_biglake"`), registered in `META-INF/services/...IcebergCatalogProvider`.
- `REQ-04`: `loadCatalog(CatalogModel, Map<String, String>)` shall reject `null` `CatalogModel` (`InvalidCatalogModelException`), reject `null`/empty `properties` or `null` keys/values (`CatalogOperationException`), and wrap any Iceberg/auth failure in `CatalogOperationException` preserving cause.
- `REQ-05`: Property validation shall require non-blank `warehouse` and a non-blank GCP project ID via **either** `header.x-goog-user-project` **or** shorthand `gcp.project` (mapping `gcp.project` → `header.x-goog-user-project`, stripping `gcp.project` before passing to Iceberg, and throwing `CatalogOperationException` if both are present with conflicting trimmed values).
- `REQ-06`: Property resolution shall enforce `type="rest"` (stripping any `catalog-impl`) and default `uri` (`https://biglake.googleapis.com/iceberg/v1/restcatalog`), `io-impl` (`org.apache.iceberg.gcp.gcs.GCSFileIO`), `rest.auth.type` (`org.apache.iceberg.gcp.auth.GoogleAuthManager`), and `header.X-Iceberg-Access-Delegation` (`vended-credentials`) without overriding explicit caller values.
- `REQ-07`: All additional caller properties (e.g., `gcp.auth.credentials-path`, `gcp.auth.scopes`, `gcs.project-id`, custom `header.*`) shall be preserved verbatim.
- `REQ-08`: Upgrading to Iceberg `1.10.1` shall maintain 100% pass rate across all existing `lakehouse-core`, `lakehouse-iceberg`, and `lakehouse-iceberg-aws` unit tests (and opt-in Docker `IcebergRestCatalogIT`).
- `REQ-09`: An opt-in `@Tag("integration")` test (`BigLakeRestCatalogIT`) shall verify live BigLake REST namespace and table creation when explicitly invoked with GCP ADC.

---

## 4. Public Java API & External Configuration Contract

### Public Type (`x.ruiz.playground.data.engineering.lakehouse.iceberg.gcp`)

| Type | Kind & Visibility | Public Constants & Methods | Contract |
| :--- | :--- | :--- | :--- |
| `GcpBigLakeCatalogProvider` | `public final class implements IcebergCatalogProvider` | `BACKEND_TYPE = "gcp_biglake"`<br/>`DEFAULT_BIGLAKE_URI = "https://biglake.googleapis.com/iceberg/v1/restcatalog"`<br/>`public GcpBigLakeCatalogProvider()`<br/>`backendType()`, `loadCatalog(CatalogModel, Map<String, String>)` | Stateless SPI provider validating `warehouse` + GCP project, applying BigLake REST + `GCSFileIO` + `GoogleAuthManager` + `vended-credentials` defaults, and loading via `CatalogUtil.buildIcebergCatalog`. |

### Property Resolution Matrix

| Property Key | Requirement | Default Value | Notes |
| :--- | :--- | :--- | :--- |
| `warehouse` | **Required** (non-blank) | — | GCS bucket/prefix (`gs://...`) or BigLake warehouse path |
| `gcp.project` *or* `header.x-goog-user-project` | **Required** (at least one) | — | `gcp.project` translates to `header.x-goog-user-project` and is stripped; mismatch throws `CatalogOperationException` |
| `type` | Enforced | `"rest"` | Always `"rest"`; removes `catalog-impl` if supplied |
| `uri` | Optional | `"https://biglake.googleapis.com/iceberg/v1/restcatalog"` | Overridable for regional/staging/mock endpoints |
| `io-impl` | Optional | `"org.apache.iceberg.gcp.gcs.GCSFileIO"` | Overridable by caller |
| `rest.auth.type` | Optional | `"org.apache.iceberg.gcp.auth.GoogleAuthManager"` | Uses Google ADC by default |
| `header.X-Iceberg-Access-Delegation` | Optional | `"vended-credentials"` | Overridable by caller |

### External Pipeline Configuration Example (`CATALOG_BACKEND=gcp_biglake`)
```properties
warehouse=gs://my-lakehouse-warehouse/
gcp.project=my-gcp-project-id
```

---

## 5. Invariants, Edge Cases & Failure Modes

### Key Invariants
1. **Strict Cloud Isolation:** `lakehouse-iceberg-gcp` has zero AWS deps; `lakehouse-iceberg-aws` has zero GCP deps; `lakehouse-iceberg` has neither.
2. **Single Version Source of Truth:** All Iceberg artifacts resolve from `${iceberg.version}` (`1.10.1`)—never mixed Iceberg versions across modules.
3. **Stateless Provider & No Leaked Shorthand:** Holds zero mutable state; `gcp.project` is never leaked into Iceberg catalog properties.
4. **Shaded Fat JAR Safety:** `ServicesResourceTransformer` merges `META-INF/services` across `lakehouse-iceberg` and `lakehouse-iceberg-gcp`.

### Failure Mode Matrix

| Failure Condition | Expected Exception | Notes |
| :--- | :--- | :--- |
| `loadCatalog(null, props)` | `InvalidCatalogModelException` | Fails fast before property validation. |
| `properties` is `null`, empty, or has `null` key/value | `CatalogOperationException` | Never leaks `NullPointerException`. |
| Missing/blank `warehouse`, missing project ID, or conflicting `gcp.project` vs. `header.x-goog-user-project` | `CatalogOperationException` | Diagnostic message names offending keys. |
| Missing ADC / unreachable BigLake URI / `403` during `CatalogUtil.buildIcebergCatalog` | `CatalogOperationException` | Preserves underlying Iceberg / Google Auth cause. |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | SPI discovery & AWS isolation | `IcebergCatalogFactory.availableBackends()` on GCP plugin classpath | Returns `["gcp_biglake", "generic", "in_memory"]` (proving AWS/MinIO providers are absent). |
| `TC-002` | Defaults with `gcp.project` shorthand | Valid `warehouse` + `gcp.project` | Resolves `type=rest`, default BigLake `uri`, `GCSFileIO`, `GoogleAuthManager`, `vended-credentials`, `header.x-goog-user-project`, and strips `gcp.project`. |
| `TC-003` | Explicit headers & caller overrides | Custom `uri`, `rest.auth.type`, delegation header, `gcp.auth.credentials-path`, matching `gcp.project` | Caller overrides and extra props preserved; `type` remains `"rest"` and `catalog-impl` stripped. |
| `TC-004` | Input & property validation errors | `null` model, `null`/empty/null-entry map, missing `warehouse`, missing project ID, or conflicting project keys | Throws `InvalidCatalogModelException` or `CatalogOperationException` naming offending keys. |
| `TC-005` | Underlying error wrapping | Unreachable local port with `rest.auth.type=none` | Wraps connection failure in `CatalogOperationException` preserving cause. |
| `TC-006` | Iceberg `1.10.1` full reactor regression | `mvn clean test` across all 4 modules | All unit tests pass on Java 17 & Iceberg `1.10.1` with 0 failures/errors. |
| `TC-007` | Strict cloud isolation & version alignment | Audit all module POMs | Zero cross-cloud deps; all Iceberg deps resolve from `${iceberg.version}`. |
| `TC-008` | Strict Javadoc lint | `mvn test javadoc:javadoc -Ddoclint=all` across all 4 modules | Succeeds with 0 errors and 0 warnings. |
| `TC-009` | Opt-in live BigLake & MinIO integration tests | Explicitly invoke `BigLakeRestCatalogIT` (GCP ADC) and `IcebergRestCatalogIT` (Docker) | Both load live `RESTCatalog`s and create namespaces/tables idempotently on Iceberg `1.10.1`; skipped during normal `mvn clean test`. |
