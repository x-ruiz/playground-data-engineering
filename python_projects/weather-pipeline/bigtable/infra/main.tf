# resource "google_bigtable_instance" "marketdata-instance" {
#   name = "marketdata-instance"

#   cluster {
#     cluster_id   = "marketdata-usc1-c"
#     num_nodes    = 1
#     storage_type = "HDD"
#     zone         = "us-central1-c"
#   }

#   cluster {
#     cluster_id   = "marketdata-usc1-b"
#     num_nodes    = 1
#     storage_type = "HDD"
#     zone         = "us-central1-b"
#   }

#   deletion_protection = false

# }
