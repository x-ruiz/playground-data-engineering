resource "google_pubsub_topic" "weather_chicago" {
  name = "weather_chicago"

  labels = {
    city = "chicago"
  }

  message_retention_duration = "86400s" # 1 day
}

resource "google_pubsub_subscription" "weather_chicago" {
  name                  = "weather_chicago"
  topic                 = google_pubsub_topic.weather_chicago.id
  ack_deadline_seconds  = 300
  retain_acked_messages = true
}
