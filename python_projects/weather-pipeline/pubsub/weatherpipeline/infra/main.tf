locals {
  region         = "us-central1"
  project_number = "670325591013"
  project_id     = "unified-gist-464917-r7"
  env_file_path  = "./.env"
}

data "local_file" "env_file" {
  filename = local.env_file_path
}

# -----------------------------------------------------------------------------
# Enable Required APIs
# -----------------------------------------------------------------------------
resource "google_project_service" "cloud_run_api" {
  service            = "run.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "cloud_scheduler_api" {
  service            = "cloudscheduler.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "pubsub_api" {
  service            = "pubsub.googleapis.com"
  disable_on_destroy = false
}

# -----------------------------------------------------------------------------
# Create a Service Account for the Cloud Run Job and apply policies
# -----------------------------------------------------------------------------
resource "google_service_account" "weather_pipeline_publisher" {
  account_id   = "weather-pipeline-publisher-sa"
  display_name = "Service Account for the Java Pub/Sub Publisher job"
}

# Grant the service account permissions to publish to the Pub/Sub topic
resource "google_pubsub_topic_iam_member" "publisher_iam" {
  topic  = google_pubsub_topic.weather_chicago.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${google_service_account.weather_pipeline_publisher.email}"
}

# Grant the service account permissions to invoke the Cloud Run Job
resource "google_project_iam_member" "cloud_run_invoker_iam" {
  project = local.project_id # or the project where the Cloud Run Job resides
  role    = "roles/run.invoker"
  member  = "serviceAccount:${google_service_account.weather_pipeline_publisher.email}"
}

# To generate OIDC tokens
resource "google_project_iam_member" "service_account_token_creator" {
  project = local.project_id
  role    = "roles/iam.serviceAccountTokenCreator"
  member  = "serviceAccount:${google_service_account.weather_pipeline_publisher.email}"
}

# -----------------------------------------------------------------------------
# Define the Cloud Run Job
# -----------------------------------------------------------------------------
resource "google_cloud_run_v2_job" "weather_pipeline_publisher" {
  name                = "weather-pipeline-publisher"
  location            = local.region
  deletion_protection = false

  template {
    template {
      containers {
        image = "us-central1-docker.pkg.dev/unified-gist-464917-r7/internal/weather-pipeline-producer:1.0.0"
        env {
          name  = "WEATHER_API_KEY"
          value = var.WEATHER_API_KEY
        }
      }
      service_account = google_service_account.weather_pipeline_publisher.email
    }
  }

  depends_on = [
    google_project_service.cloud_run_api,
    google_pubsub_topic_iam_member.publisher_iam
  ]
}

# -----------------------------------------------------------------------------
# Create the Cloud Scheduler Job to trigger the Cloud Run Job
# -----------------------------------------------------------------------------
resource "google_cloud_scheduler_job" "weather_pipeline_publisher" {
  name        = "weather-pipeline-publisher"
  description = "Triggers the Cloud Run Job to publish to Pub/Sub"

  schedule = "0 * * * *"

  region = "us-central1"

  http_target {
    uri         = "https://${local.region}-run.googleapis.com/apis/run.googleapis.com/v1/namespaces/${local.project_number}/jobs/${google_cloud_run_v2_job.weather_pipeline_publisher.name}:run"
    http_method = "POST"

    # OAUTH token for authentication
    oauth_token {
      service_account_email = google_service_account.weather_pipeline_publisher.email
    }
  }
}
