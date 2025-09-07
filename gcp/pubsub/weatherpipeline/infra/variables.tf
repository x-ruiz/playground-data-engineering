###  This is being passed from export TF_VAR_WEATHER_API_KEY from shell
variable "WEATHER_API_KEY" {
  type        = string
  description = "API Key to access weather service"
  sensitive   = true
}
