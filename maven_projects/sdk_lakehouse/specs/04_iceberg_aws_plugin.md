# Feature Spec: `lakehouse-iceberg-aws` SPI Plugin (Local MinIO & AWS REST)

- **Status:** Approved
- **Module / Component:** `lakehouse-iceberg-aws` (Thin AWS / Local MinIO SPI Plugin — Java 17)
- **Archetype:** Java Library Contract (Cloud SPI Plugin)
- **Tier:** Mini-Spec
- **Owner:** Xavier Ruiz
- **Depends On:** `specs/00_project_structure_and_organization.md`, `specs/03_iceberg_core_adapter_and_spi.md`

---

## 1. Goal & Context

### Problem Statement
`lakehouse-iceberg` (Spec 03) provides cloud-agnostic Iceberg managers and the `IcebergCatalogProvider` SPI. To run unified GitOps pipelines against local Docker Iceberg REST + MinIO sandboxes and production AWS Iceberg REST catalogs—without polluting `lakehouse-iceberg` or future GCP runtime JARs with AWS SDK dependencies—we need a thin sibling plugin module (`lakehouse-iceberg-aws`) discovered automatically via `java.util.ServiceLoader`.

### Success Metrics
- `lakehouse-iceberg-aws` cleanly plugs into `IcebergCatalogFactory` via `META-INF/services`, discovering `"local_minio"` and `"aws_rest"` alongside built-in providers (`"generic"`, `"in_memory"`).
- All unit tests (`TC-001` through `TC-005`) pass offline with zero Docker or AWS credentials and zero GCP dependencies.
- Opt-in live Docker REST + MinIO integration test (`IcebergRestCatalogIT`, `TC-006`) verifies end-to-end namespace and table creation without running during normal `mvn clean test`.

---

## 2. Scope & Explicit Non-Goals

### In Scope
- Thin Maven module `lakehouse-iceberg-aws` registered in root reactor `pom.xml`, depending on `lakehouse-iceberg`, `iceberg-aws`, and `iceberg-aws-bundle` (with `maven-shade-plugin` `ServicesResourceTransformer`).
- Package `x.ruiz.playground.data.engineering.lakehouse.iceberg.aws`:
  - `LocalMinioCatalogProvider` (`public final class implements IcebergCatalogProvider`, backend `"local_minio"`).
  - `AwsRestCatalogProvider` (`public final class implements IcebergCatalogProvider`, backend `"aws_rest"`).
- `META-INF/services/x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogProvider` registering both providers.
- Offline property validation/defaulting unit tests plus opt-in `@Tag("integration")` Docker `IcebergRestCatalogIT`.

### Non-Goals (Do Not Implement)
- **No modifications to `lakehouse-core` or `lakehouse-iceberg` managers:** Existing managers operate unchanged against REST catalogs.
- **No AWS Glue or legacy Hive metastore providers yet:** Scoped strictly to Iceberg REST + `S3FileIO` (`local_minio` and `aws_rest`).
- **No GCP dependencies:** Zero `iceberg-gcp` or Google Cloud SDK dependencies in `lakehouse-iceberg-aws/pom.xml`.
- **No automatic Docker execution during `mvn clean test`:** Live Docker tests remain strictly opt-in.

---

## 3. Functional Requirements

- `REQ-01`: `lakehouse-iceberg-aws` shall depend on `lakehouse-iceberg`, `iceberg-aws`, and `iceberg-aws-bundle`, contain zero GCP dependencies, and configure `maven-shade-plugin` with `ServicesResourceTransformer` so shaded fat JARs merge `META-INF/services` entries cleanly.
- `REQ-02`: `LocalMinioCatalogProvider` (`backendType() == "local_minio"`) shall reject `null` `CatalogModel` (`InvalidCatalogModelException`), require non-blank `uri`, `warehouse`, and `s3.endpoint` (rejecting missing/blank required keys or `null` keys/values with `CatalogOperationException`), default `type=rest`, `io-impl=org.apache.iceberg.aws.s3.S3FileIO`, and `s3.path-style-access=true`, preserve caller properties (e.g., credentials/region), and load via `CatalogUtil.buildIcebergCatalog`.
- `REQ-03`: `AwsRestCatalogProvider` (`backendType() == "aws_rest"`) shall reject `null` `CatalogModel` (`InvalidCatalogModelException`), require non-blank `uri` and `warehouse` (rejecting missing/blank keys or `null` entries with `CatalogOperationException`), default `type=rest` and `io-impl=org.apache.iceberg.aws.s3.S3FileIO`, default `rest.sigv4-enabled=true` when `rest.signing-region` is present and `rest.sigv4-enabled` is omitted, preserve caller properties, and load via `CatalogUtil.buildIcebergCatalog`.
- `REQ-04`: Both providers shall be registered in `META-INF/services/...IcebergCatalogProvider` for automatic `ServiceLoader` discovery.
- `REQ-05`: Both providers shall wrap any underlying Iceberg/S3 initialization failure in `CatalogOperationException` preserving the original cause.
- `REQ-06`: An opt-in `@Tag("integration")` test (`IcebergRestCatalogIT`) shall verify live namespace and partitioned table creation against local Docker Iceberg REST + MinIO without executing during standard `mvn clean test`.

---

## 4. Public Java API & External Configuration Contract

### Public Types (`x.ruiz.playground.data.engineering.lakehouse.iceberg.aws`)

| Type | Kind & Visibility | Backend ID | Required Keys | Applied Defaults |
| :--- | :--- | :--- | :--- | :--- |
| `LocalMinioCatalogProvider` | `public final class implements IcebergCatalogProvider` | `"local_minio"` | `uri`, `warehouse`, `s3.endpoint` | `type=rest`, `io-impl=org.apache.iceberg.aws.s3.S3FileIO`, `s3.path-style-access=true` |
| `AwsRestCatalogProvider` | `public final class implements IcebergCatalogProvider` | `"aws_rest"` | `uri`, `warehouse` | `type=rest`, `io-impl=org.apache.iceberg.aws.s3.S3FileIO`; `rest.sigv4-enabled=true` when `rest.signing-region` is set and not overridden |

### External Pipeline Configuration Examples

**Local Dev (`CATALOG_BACKEND=local_minio`):**
```properties
uri=http://localhost:8181
warehouse=s3://warehouse/
s3.endpoint=http://localhost:9000
client.region=us-east-1
s3.access-key-id=admin
s3.secret-access-key=password
```

**AWS Production (`CATALOG_BACKEND=aws_rest`):**
```properties
uri=https://iceberg-rest.internal.example.com
warehouse=s3://prod-lakehouse-warehouse/
rest.signing-region=us-east-1
rest.signing-name=execute-api
```

---

## 5. Invariants, Edge Cases & Failure Modes

### Key Invariants
1. **Strict Cloud Isolation:** `lakehouse-iceberg-aws` depends only on `lakehouse-iceberg` + AWS Iceberg artifacts (zero GCP deps); `lakehouse-iceberg` never depends on `lakehouse-iceberg-aws`.
2. **Stateless Providers:** Zero mutable state or cached credentials; callers own closing returned `AutoCloseable` catalogs.
3. **Shaded Fat JAR Safety:** `ServicesResourceTransformer` preserves `META-INF/services` registrations across both `lakehouse-iceberg` and `lakehouse-iceberg-aws`.
4. **Explicit Override Respect:** Caller-provided values (e.g., explicit `rest.sigv4-enabled=false` or custom `io-impl`) are never silently clobbered.

### Failure Mode Matrix

| Failure Condition | Expected Exception | Notes |
| :--- | :--- | :--- |
| `null` `CatalogModel` | `InvalidCatalogModelException` | Fails fast before property validation. |
| `null`/empty map, `null` key/value, or missing/blank required key (`uri`, `warehouse`, `s3.endpoint`) | `CatalogOperationException` | Diagnostic message names the offending key. |
| Unreachable REST URI or S3/Iceberg initialization error | `CatalogOperationException` | Preserves underlying cause. |

---

## 6. Acceptance Criteria & Verification Matrix

| Case ID | Scenario | Input / Precondition | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TC-001` | `ServiceLoader` discovery in `lakehouse-iceberg-aws` | `IcebergCatalogFactory.availableBackends()` on AWS plugin classpath | Returns `["aws_rest", "generic", "in_memory", "local_minio"]` (no GCP backend present). |
| `TC-002` | `LocalMinioCatalogProvider` defaults & validation | Valid MinIO props, explicit overrides, missing `uri`/`warehouse`/`s3.endpoint`, and `null` keys/values | Applies `type=rest`, `S3FileIO`, `s3.path-style-access=true`, preserves credentials/region; invalid inputs throw `CatalogOperationException` / `InvalidCatalogModelException`. |
| `TC-003` | `AwsRestCatalogProvider` SigV4 defaults & validation | Props with/without `rest.signing-region`, explicit `rest.sigv4-enabled=false`, and missing `uri`/`warehouse` | Defaults `rest.sigv4-enabled=true` only when signing region is set and flag omitted; invalid inputs throw `CatalogOperationException`. |
| `TC-004` | Underlying error wrapping | Unreachable REST endpoint passed to provider `loadCatalog` | Throws `CatalogOperationException` preserving original cause. |
| `TC-005` | Cloud isolation & strict Javadoc hygiene | Audit `lakehouse-iceberg-aws/pom.xml` and run `mvn test javadoc:javadoc -pl lakehouse-iceberg-aws -Ddoclint=all` | Zero GCP deps, `ServicesResourceTransformer` configured, Javadoc passes with 0 errors/warnings. |
| `TC-006` | Opt-in Docker REST + MinIO integration test (`IcebergRestCatalogIT`) | Local `docker compose up` (`:8181`, `:9000`); run `-Dtest=IcebergRestCatalogIT` | Loads live `RESTCatalog` via `IcebergCatalogFactory.loadCatalog("local_minio", ...)`, creates namespace & partitioned table idempotently; skipped during normal `mvn clean test`. |
