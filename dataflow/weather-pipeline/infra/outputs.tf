output "bigquery_connection_service_account" {
  description = "The service account email of the BigQuery connection."
  value       = google_bigquery_connection.cloud_storage.cloud_resource[0].service_account_id
}

output "bigquery_connection_name" {
  description = "The full resource name of the BigQuery connection."
  value       = google_bigquery_connection.cloud_storage.name
}

output "bigquery_connection_id" {
  description = "The full resource name of the BigQuery connection."
  value       = google_bigquery_connection.cloud_storage.id
}
