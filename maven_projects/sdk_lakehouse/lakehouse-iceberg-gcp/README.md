# `lakehouse-iceberg-gcp` — GCP BigLake Iceberg REST SPI Plugin

Thin `ServiceLoader` plugin module adding **Google Cloud BigLake Iceberg REST Catalog** support (`backend = "gcp_biglake"`) to `lakehouse-iceberg` with zero AWS SDK dependencies.

- **SPI Provider:** `x.ruiz.playground.data.engineering.lakehouse.iceberg.gcp.GcpBigLakeCatalogProvider`
- **Registered In:** `src/main/resources/META-INF/services/x.ruiz.playground.data.engineering.lakehouse.iceberg.catalog.IcebergCatalogProvider`
- **Iceberg Dependencies:** `iceberg-gcp` + `iceberg-gcp-bundle` (`GCSFileIO` + native `GoogleAuthManager`)
- **Spec:** [../specs/03_iceberg_gcp_biglake_plugin.md](../specs/03_iceberg_gcp_biglake_plugin.md)

---

## Configuration Properties (`backend = "gcp_biglake"`)

| Property | Required | Default | Notes |
| :--- | :--- | :--- | :--- |
| `warehouse` | Yes | — | GCS bucket/prefix (`gs://...`) or BigLake warehouse path |
| `gcp.project` *or* `header.x-goog-user-project` | Yes | — | Billing/routing GCP project (`gcp.project` is translated to `header.x-goog-user-project`) |
| `uri` | No | `https://biglake.googleapis.com/iceberg/v1/restcatalog` | Overridable for regional or staging endpoints |
| `io-impl` | No | `org.apache.iceberg.gcp.gcs.GCSFileIO` | Overridable by caller |
| `rest.auth.type` | No | `org.apache.iceberg.gcp.auth.GoogleAuthManager` | Uses Google ADC by default; supports `gcp.auth.credentials-path` and `gcp.auth.scopes` |
| `header.X-Iceberg-Access-Delegation` | No | `vended-credentials` | Overridable by caller |

---

## Running Unit Tests (Offline — No GCP Credentials Required)

```bash
mvn test -pl lakehouse-iceberg-gcp -am
```

Verifies `ServiceLoader` SPI discovery (`["gcp_biglake", "generic", "in_memory"]`, confirming AWS backends are absent), BigLake defaults/overrides, property validation, and error wrapping.

---

## Testing End-to-End Against Live Google Cloud BigLake

### 1. Authenticate Local Application Default Credentials (ADC)
`GoogleAuthManager` uses **Application Default Credentials** (not just `gcloud auth login`) and requires a quota/billing project:

```bash
export GCP_PROJECT="your-gcp-project-id"
export GCS_BUCKET="your-unique-lakehouse-bucket"

gcloud auth application-default login
gcloud auth application-default set-quota-project "$GCP_PROJECT"
```

### 2. Enable APIs & Verify IAM Roles
```bash
gcloud services enable biglake.googleapis.com storage.googleapis.com --project="$GCP_PROJECT"
```

Required IAM roles for your user or service account:
- `roles/biglake.admin` (manage BigLake Iceberg catalogs, namespaces, and tables)
- `roles/storage.objectAdmin` or `roles/storage.objectUser` on `gs://$GCS_BUCKET`

### 3. Provision GCS Bucket & BigLake Iceberg Catalog (Platform Step)
```bash
gcloud storage buckets create "gs://$GCS_BUCKET" \
  --project="$GCP_PROJECT" \
  --location=US \
  --uniform-bucket-level-access

gcloud beta biglake iceberg catalogs create "$GCS_BUCKET" \
  --project="$GCP_PROJECT" \
  --catalog-type=gcs-bucket \
  --credential-mode=vended-credentials
```

> **Credential Mode Note:** If BigLake provisions a Google-managed catalog service account for `vended-credentials`, grant that service account `roles/storage.objectUser` on `gs://$GCS_BUCKET`. Alternatively, pass `--credential-mode=end-user` to access GCS directly with your own ADC.
>
> Confirm catalog details anytime with:
> ```bash
> gcloud beta biglake iceberg catalogs describe "$GCS_BUCKET" --project="$GCP_PROJECT"
> ```

### 4. Run the Opt-In Integration Test (`BigLakeRestCatalogIT`)
Executes `BigLakeRestCatalogIT` against your live BigLake catalog using the YAML fixtures in `src/test/resources/` (`namespace.yaml` and `table.yaml`):

```bash
mvn test -pl lakehouse-iceberg-gcp -am \
  -Dtest=BigLakeRestCatalogIT \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dbiglake.warehouse="gs://$GCS_BUCKET" \
  -Dbiglake.project="$GCP_PROJECT"
```

This verifies end-to-end:
1. `ServiceLoader` discovers `GcpBigLakeCatalogProvider` (`"gcp_biglake"`) with zero AWS JARs on the classpath.
2. `GoogleAuthManager` authenticates to `https://biglake.googleapis.com/iceberg/v1/restcatalog` via ADC with `x-goog-user-project: $GCP_PROJECT`.
3. `IcebergNamespaceManager` parses `namespace.yaml` and creates `market_data` idempotently.
4. `IcebergTableManager` parses `table.yaml` and creates partitioned table `market_data.transactions` (`DECIMAL(38, 9)` + `DATE` identity partition) idempotently.

### 5. Verify & Clean Up Resources
Inspect namespaces, tables, and GCS metadata:
```bash
gcloud beta biglake iceberg namespaces list --catalog="$GCS_BUCKET" --project="$GCP_PROJECT"
gcloud beta biglake iceberg tables list --catalog="$GCS_BUCKET" --namespace=market_data --project="$GCP_PROJECT"
gcloud storage ls -r "gs://$GCS_BUCKET/**"
```

Clean up test resources when finished:
```bash
gcloud beta biglake iceberg tables delete transactions --catalog="$GCS_BUCKET" --namespace=market_data --project="$GCP_PROJECT"
gcloud beta biglake iceberg namespaces delete market_data --catalog="$GCS_BUCKET" --project="$GCP_PROJECT"
```
