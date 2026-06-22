from airflow.sdk import dag, task
from airflow.sdk.definitions.asset import Asset
from airflow.exceptions import AirflowException

import requests
import os
import pendulum
import json
import subprocess

from google.cloud import bigtable
from google.cloud.bigtable.data import row_filters
from google.cloud.bigtable.table import Table


gcp_project = "unified-gist-464917-r7"
 
@dag(
    dag_id="marketdata_etl_v2",
    start_date=pendulum.datetime(2023, 10, 26, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    schedule="@once",
    tags=["etl"],
)
def marketdata_etl():
    @task
    def api_setup() -> str:
        print("Setting up environment variables")
        access_key = os.environ.get("API_MARKETSTACK_KEY", default=None)
        if access_key == None:
            raise AirflowException("API_MARKETSTACK_KEY not found or set")
        print("API_MARKETSTACK_KEY found")

        ### Base Variables ###
        symbols = "AAPL"
        exchange = "nasdaw"
        api_url = f"http://api.marketstack.com/v2/stockprice/?access_key={access_key}&ticker={symbols}&exchange={exchange}"

        ### Set Up GCP Connection ###
        # subprocess.run(["gcloud auth application-default login"], shell=True, capture_output=True)
        return api_url
    
    @task()
    def bigtable_setup():
        # Constant Variables
        instance_id = "emulator-instance"
        table_id = "emulator-marketdata-table"
        column_family_id = "cf1"

        # Connect to bigtable emulator
        client = bigtable.Client(project=gcp_project, admin=True)
        instance = client.instance(instance_id)

        print(f"Checking if table '{table_id}' exists...")
        table = instance.table(table_id)

        if table.exists():
            print(f"Table '{table_id}' already exists. Deleting it...")
            table.delete()
            print(f"Table '{table_id}' deleted.")

        print(f"Creating table '{table_id}'...")
        table.create(column_families={column_family_id: None})
        print(f"Table '{table_id}' and column family '{column_family_id}' created successfully.")

        return {"instance_id": instance_id, "table_id": table_id, "column_family_id": column_family_id}


    @task
    def extract(api_url: str) -> list[dict]:
        print("Running extract step")
        response = requests.get(api_url)
        print(response.status_code)
        data = response.json()
        print(json.dumps(data))
        return data
    
    @task
    def get_request_time() -> pendulum.DateTime:
        timestamp = pendulum.now()
        print("Request time", timestamp)
        return timestamp
    
    @task
    def transform(data: list[dict], timestamp: pendulum.DateTime) -> list[dict]:
        print("Running transform step")
        # Add request time
        for subset in data:
            subset["request_time"] = timestamp.to_datetime_string()

        return data
    
    @task
    def load(transformed_data: list[dict], bigtable_info: dict):
        print(f"Loading data into table: {bigtable_info['table_id']}", transformed_data)

        # Recreate the connection and table object inside this task
        instance_id = bigtable_info["instance_id"]
        table_id = bigtable_info["table_id"]
        column_family_id = bigtable_info["column_family_id"]

        client = bigtable.Client(project=gcp_project, admin=True)
        instance = client.instance(instance_id)
        table = instance.table(table_id)

        rows = []
        for record in transformed_data:
            # Create the row key per row
            row_key = f"{record['ticker']}_{record['request_time']}"
            row = table.direct_row(row_key.encode('utf-8'))

            for key, value in record.items(): # add cells for each key value pair in dict
                row.set_cell(column_family_id, key.encode("utf-8"), str(value).encode("utf-8"))
            rows.append(row)

        # Mutate the table to insert the prepared rows
        # this batches the row updates together in one network request for efficiency
        try:
            table.mutate_rows(rows)
            print(f"Successfully loaded {len(rows)} rows into {table_id}")
        except Exception as e:
            print(f"Failed to load rows: {e}")
            raise AirflowException("Bigtable load failed.")

        print("Load successful!")

    # url = api_setup()
    table_info = bigtable_setup()
    # raw_data = extract(api_url=url) # type: ignore
    raw_data = [
        {
            "exchange_code": "NASDAQ",
            "exchange_name": "Nasdaq Stock Market",
            "country": "United States",
            "ticker": "AAPL",
            "price": "244.07",
            "currency": "USD",
            "trade_last": "2025-02-14 15:03:45"
        }
    ]
    timestamp = get_request_time()
    transformed_data = transform(raw_data, timestamp) # type: ignore
    load(transformed_data, table_info) # type: ignore

marketdata_etl()