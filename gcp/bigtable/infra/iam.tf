resource "google_service_account" "bigtable_etl" {
  account_id   = "sv-bigtable-etl-0001"
  display_name = "Service account used to ingest data into BigTable"
}
