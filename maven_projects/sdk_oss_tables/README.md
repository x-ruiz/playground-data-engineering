# Proposed Project Structure
```mermaid
flowchart LR
    Root["playground-data-engineering/"]
    RootPOM["pom.xml<br/>(Root Parent POM)"]

%% Grouping Folder
    ProjectDir["iceberg-lakehouse/<br/>(or project-iceberg/)"]
    ProjectPOM["pom.xml<br/>(Project Aggregator POM)"]

    Root --> RootPOM
    Root --> ProjectDir
    ProjectDir --> ProjectPOM

%% Submodules
    SDK["iceberg-sdk/<br/>(Reusable Client Library)"]
    App["iceberg-app/<br/>(Runnable Entrypoint / Runner)"]

    ProjectDir --> SDK
    ProjectDir --> App

%% SDK Internals
    SDKPOM["pom.xml<br/>(spark-sql: provided)"]
    SDKSrc["src/.../iceberg/"]
    SDK --> SDKPOM
    SDK --> SDKSrc

    TableAPI["IcebergTableOperations.java<br/>(Table Interface)"]
    Config["config/IcebergTableConfig.java<br/>(Config + Builder)"]
    IceTable["IcebergTable.java<br/>(Table DDL / DML Engine)"]
    SDKSrc --> TableAPI
    SDKSrc --> Config
    SDKSrc --> IceTable

%% App Internals
    AppPOM["pom.xml<br/>(Depends on: iceberg-sdk)"]
    AppSrc["src/.../app/"]
    App --> AppPOM
    App --> AppSrc

    Factory["factory/SparkSessionFactory.java<br/>(Docker / MinIO / REST Connection)"]
    Main["Main.java<br/>(spark-submit CLI Entrypoint)"]
    AppSrc --> Factory
    AppSrc --> Main

%% Styling
    classDef folder fill:#E3F2FD,stroke:#1E88E5,stroke-width:2px,color:#0D47A1;
    classDef file fill:#F5F5F5,stroke:#9E9E9E,stroke-width:1px,color:#212121;

    class Root,ProjectDir,SDK,App,SDKSrc,AppSrc folder;
    class RootPOM,ProjectPOM,SDKPOM,TableAPI,Config,IceTable,AppPOM,Factory,Main file;
```
# Set Up
https://iceberg.apache.org/spark-quickstart/

`docker-compose up` -> sets up spark, iceberg rest catalog, and minio (storage) \
`docker exec -it spark-iceberg spark-sql`

This sets up a Iceberg REST catalog in spark called demo.

The docker-compose creates the below setup:
```mermaid
flowchart TD
    subgraph DockerNetwork[Docker Network]
        %% Compute Layer
        subgraph ComputeLayer[Compute Layer]
            Spark[tabulario/spark-iceberg<br/>• Spark Master & Worker<br/>• Iceberg Runtime<br/>• Spark SQL Engine]
        end

        %% Catalog & Storage Layer
        subgraph DataLayer[Catalog & Storage Layer]
            Catalog[apache/iceberg-rest-fixture<br/>REST Catalog - Port 8181<br/>• ACID Commits<br/>• Table Metadata Pointers<br/>• Namespace Registry]
            
            Storage[minio/minio<br/>S3 Object Storage - Port 9000/9001<br/>• Data Files .parquet<br/>• Manifest Lists .avro<br/>• Metadata JSON .metadata.json]
        end

        %% Init Layer
        subgraph InitLayer[Bootstrap / Setup]
            MC[minio/mc<br/>MinIO Client CLI<br/>• Auto-creates warehouse bucket<br/>• Configures access policies]
        end

        %% Flow & Interactions (Quotes resolve the parenthesis parse error)
        Spark -->|"1. Fetch / Commit Metadata (REST API)"| Catalog
        Spark -->|"2. Read / Write Data & Manifests (S3FileIO)"| Storage
        MC -.->|"Creates bucket & initializes"| Storage
    end

    %% Styles
    classDef compute fill:#E3F2FD,stroke:#1E88E5,stroke-width:2px,color:#0D47A1;
    classDef catalog fill:#EDE7F6,stroke:#5E35B1,stroke-width:2px,color:#311B92;
    classDef storage fill:#E8F5E9,stroke:#43A047,stroke-width:2px,color:#1B5E20;
    classDef init fill:#FFF3E0,stroke:#FB8C00,stroke-width:2px,color:#E65100;

    class Spark compute;
    class Catalog catalog;
    class Storage storage;
    class MC init;
```

## Creating a database
`CREATE DATABASE IF NOT EXISTS demo.icebergingestion;`

## Creating a table
```sparksql
CREATE TABLE demo.icebergingestion.taxis
(
  vendor_id bigint,
  trip_id bigint,
  trip_distance float,
  fare_amount double,
  store_and_fwd_flag string
)
PARTITIONED BY (vendor_id);
```

## Writing data
```sparksql
INSERT INTO demo.icebergingestion.taxis
VALUES (1, 1000371, 1.8, 15.32, 'N'), (2, 1000372, 2.5, 22.15, 'N'), (2, 1000373, 0.9, 9.01, 'N'), (1, 1000374, 8.4, 42.13, 'Y');
```