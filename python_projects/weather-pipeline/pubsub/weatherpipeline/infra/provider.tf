terraform {
  backend "gcs" {
    bucket = "tf-state-17xh"
    prefix = "pubsub/weather-pipeline"
  }
}
provider "google" {
  project = "unified-gist-464917-r7"
  region  = "us-central1"
}


