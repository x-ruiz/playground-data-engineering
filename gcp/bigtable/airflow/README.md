# Structure
Create an airflow pipeline deployed through cloud composer that requests time series data from an api and performs
basic row key transformations to push to bigtable. Run the workflow on a cron once an hour

# Set Up
https://airflow.apache.org/docs/apache-airflow/stable/installation/index.html
https://hub.docker.com/r/apache/airflow
https://airflow.apache.org/docs/apache-airflow/stable/howto/docker-compose/index.html

```astro dev start```
```astro dev stop```

# BigTable Integration
https://cloud.google.com/bigtable/docs/samples-python-hello

## Emulator
```gcloud components update beta```
```gcloud components install bigtable```

```gcloud beta emulators bigtable start```

export BIGTABLE_EMULATOR_HOST=localhost:8086

or

```docker run -p 127.0.0.1:8086:8086 --rm -ti google/cloud-sdk gcloud beta emulators bigtable start```
```export BIGTABLE_EMULATOR_HOST=localhost:8086```

## CBT Installation
```gcloud components update```
```gcloud components install cbt```

## CBT Usage
List tables: ```cbt -project unified-gist-464917-r7 -instance emulator-instance ls```

List rows in table

```cbt -instance emulator-instance read emulator-marketdata-table```
2025/08/31 23:37:07 -creds flag unset, will use gcloud credential
2025/08/31 23:37:07 -project flag unset, will use gcloud active project
2025/08/31 23:37:07 gcloud active project is "unified-gist-464917-r7"
----------------------------------------
AAPL_2025-09-01 04:34:22
  cf1:country                              @ 2025/08/31-23:34:24.438000
    "United States"
  cf1:currency                             @ 2025/08/31-23:34:24.438000
    "USD"
  cf1:exchange_code                        @ 2025/08/31-23:34:24.438000
    "NASDAQ"
  cf1:exchange_name                        @ 2025/08/31-23:34:24.438000
    "Nasdaq Stock Market"
  cf1:price                                @ 2025/08/31-23:34:24.438000
    "244.07"
  cf1:request_time                         @ 2025/08/31-23:34:24.438000
    "2025-09-01 04:34:22"
  cf1:ticker                               @ 2025/08/31-23:34:24.438000
    "AAPL"
  cf1:trade_last                           @ 2025/08/31-23:34:24.438000
