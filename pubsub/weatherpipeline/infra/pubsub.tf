resource "google_pubsub_topic" "weather_chicago_night" {
  name = "weather_chicago_night"

  labels = {
    city        = "chicago"
    time_of_day = "night"
  }

  message_retention_duration = "86400s" # 1 day
}

resource "google_pubsub_subscription" "weather_chicago_night" {
  name                 = "weather_chicago_night"
  topic                = google_pubsub_topic.weather_chicago_night.id
  ack_deadline_seconds = 20
}
