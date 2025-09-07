locals {
  bq_connection_storage_roles = toset(["roles/storage.objectUser", "roles/storage.legacyBucketReader"])
}

resource "google_bigquery_dataset" "dataflow" {
  project    = local.project_id
  dataset_id = "dataflow"
  location   = local.region
}

###
# BigQuery Managed Iceberg Set Up
###
resource "random_string" "bucket_suffix" {
  length  = 4
  special = false
  upper   = false
}

resource "google_storage_bucket" "raw_data" {
  name     = "raw_data_${random_string.bucket_suffix.result}"
  location = local.region
}

resource "google_storage_bucket_iam_member" "raw_data" {
  for_each = local.bq_connection_storage_roles

  bucket = google_storage_bucket.raw_data.name
  role   = each.key
  member = "serviceAccount:${google_bigquery_connection.cloud_storage.cloud_resource[0].service_account_id}"
}

resource "google_bigquery_connection" "cloud_storage" {
  location = local.region
  project  = local.project_id
  cloud_resource {}
}

resource "google_bigquery_table" "weather_pipeline_raw" {
  dataset_id = google_bigquery_dataset.dataflow.dataset_id
  table_id   = "weather_pipeline_raw"
  project    = local.project_id

  deletion_protection = false

  schema = file("schemas/weather-pipeline.json")
  biglake_configuration {
    connection_id = google_bigquery_connection.cloud_storage.id
    storage_uri   = "gs://${google_storage_bucket.raw_data.name}/${google_bigquery_dataset.dataflow.dataset_id}/weather-pipeline"
    file_format   = "PARQUET"
    table_format  = "ICEBERG"
  }
}
