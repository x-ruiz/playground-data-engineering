# Lakehouse SDK (`sdk.lakehouse`)

A modular Java SDK and execution framework for managing Apache Iceberg open-source table formats, schemas, and lifecycle operations across distributed compute engines (Apache Spark) and REST catalogs backed by S3/MinIO object storage.

| Conceptual Feature | Apache Iceberg | Delta Lake | Apache Hudi |
| :--- | :--- | :--- | :--- |
| **Point in Time** | Snapshot | Version | Commit / Instant |
| **Active Metadata Root** | `metadata.json` | `_delta_log/000...json` | `.hoodie/timeline` |
| **File Listing Manifest** | Manifest List + Manifests | Checkpoint Parquet files | Timeline metadata |
| **Compaction Command** | `rewrite_data_files` | `OPTIMIZE` | Compaction / Clustering |
| **Cleanup Command** | `expire_snapshots` + `remove_orphan_files` | `VACUUM` | Cleaner |
| **Row-Level Deletes** | Position & Equality Deletes | Deletion Vectors | Merge-on-Read (Delta Log) |
| **Default Catalog** | REST Catalog / Glue / Hive | Unity Catalog / Path-based | Hive Metastore / Glue |
| **Primary File Format** | Parquet (or ORC, Avro) | Parquet | Parquet (or HFile, ORC) |

---

## Architecture & Project Structure

The project is structured as a Maven multi-module architecture adhering to clean separation of concerns between declarative domain models and compute execution.

```mermaid
flowchart TD
    subgraph Root["sdk.lakehouse (Parent Aggregator)"]
        
        subgraph ModelsModule["lakehouse-models (Domain Contracts & Models)"]
            TableIntf["Table<br/>(Core Contract Interface)"]
            IcebergTableCls["IcebergTable & IcebergTable.Builder<br/>(Immutable Table Descriptor & Fluent Builder)"]
            CatalogTypeEnum["CatalogType<br/>(REST, GLUE, JDBC, HIVE, NESSIE, HADOOP)"]
            InvalidTableEx["InvalidTableException<br/>(Domain Validation Exception)"]
            
            IcebergTableCls -.->|"implements"| TableIntf
        end

        subgraph CoreModule["lakehouse-core (Execution Engine & Shaded Fat JAR)"]
            MainApp["Main<br/>(CLI & Job Entrypoint)"]
            TableMgrIntf["session/TableManager<br/>(DDL & Table Lifecycle Contract)"]
            IcebergTableMgr["session/IcebergTableManager<br/>(Spark Session & REST/S3 Execution)"]
            
            IO["io/<br/>(Write / Upsert / Append)"]
            Maintenance["maintenance/<br/>(Compaction & Snapshot Expiry)"]
            
            MainApp -->|"creates model via Builder"| IcebergTableCls
            MainApp -->|"invokes"| TableMgrIntf
            IcebergTableMgr -.->|"implements"| TableMgrIntf
            IcebergTableMgr -->|"accepts"| TableIntf
        end

    end

    CoreModule -.->|"depends on"| ModelsModule
    IcebergTableMgr -->|"executes DDL / queries"| SparkEngine["Spark Engine & REST Catalog"]

    classDef module fill:#E3F2FD,stroke:#1E88E5,stroke-width:2px,color:#0D47A1;
    classDef contract fill:#EDE7F6,stroke:#5E35B1,stroke-width:1.5px,color:#311B92;
    classDef concrete fill:#F5F5F5,stroke:#757575,stroke-width:1px,color:#212121;
    classDef ext fill:#FFF3E0,stroke:#FB8C00,stroke-width:1.5px,color:#E65100;

    class ModelsModule,CoreModule module;
    class TableIntf,TableMgrIntf contract;
    class IcebergTableCls,IcebergTableMgr,MainApp,CatalogTypeEnum,InvalidTableEx concrete;
    class SparkEngine,IO,Maintenance ext;
```

### Module Responsibilities

| Module | Purpose | Key Components |
| :--- | :--- | :--- |
| **`lakehouse-models`** | Pure, engine-agnostic domain contracts, metadata descriptors, and builders. | [`Table`](lakehouse-models/src/main/java/x/ruiz/playground/data/engineering/lakehouse/iceberg/Table.java), [`IcebergTable`](lakehouse-models/src/main/java/x/ruiz/playground/data/engineering/lakehouse/iceberg/IcebergTable.java) (`Builder`), [`CatalogType`](lakehouse-models/src/main/java/x/ruiz/playground/data/engineering/lakehouse/iceberg/CatalogType.java), [`InvalidTableException`](lakehouse-models/src/main/java/x/ruiz/playground/data/engineering/lakehouse/iceberg/InvalidTableException.java) |
| **`lakehouse-core`** | Compute execution, catalog connection management, Spark session initialization, and deployment packaging. | [`Main`](lakehouse-core/src/main/java/x/ruiz/playground/data/engineering/lakehouse/core/Main.java), [`TableManager`](lakehouse-core/src/main/java/x/ruiz/playground/data/engineering/lakehouse/core/session/TableManager.java), [`IcebergTableManager`](lakehouse-core/src/main/java/x/ruiz/playground/data/engineering/lakehouse/core/session/IcebergTableManager.java) |

---

## Infrastructure Topology

The local development environment runs via `docker-compose` combining Apache Spark, the Apache Iceberg REST Catalog fixture, and MinIO S3 storage.

```mermaid
flowchart TD
    subgraph DockerNetwork["Docker Network (iceberg_net)"]
        %% Compute Layer
        subgraph ComputeLayer["Compute Layer"]
            Spark["spark-iceberg (tabulario/spark-iceberg)<br/>• Spark Master & Worker (Port 7077, 8080)<br/>• Iceberg Runtime<br/>• Spark SQL Engine"]
        end

        %% Catalog & Storage Layer
        subgraph DataLayer["Catalog & Storage Layer"]
            Catalog["iceberg-rest (apache/iceberg-rest-fixture)<br/>• REST Catalog - Port 8181<br/>• ACID Commits & Namespace Registry"]
            Storage["minio (minio/minio)<br/>• S3 Object Storage - Port 9000/9001<br/>• Warehouse Bucket: s3://warehouse/"]
        end

        %% Init Layer
        subgraph InitLayer["Bootstrap / Setup"]
            MC["mc (minio/mc)<br/>• Auto-provisions 'warehouse' bucket<br/>• Configures public access policies"]
        end

        %% Interactions
        Spark -->|"1. Commit / Fetch Table Metadata (REST API)"| Catalog
        Spark -->|"2. Read / Write Data Files & Manifests (S3FileIO)"| Storage
        MC -.->|"Initializes bucket"| Storage
    end

    classDef compute fill:#E3F2FD,stroke:#1E88E5,stroke-width:2px,color:#0D47A1;
    classDef catalog fill:#EDE7F6,stroke:#5E35B1,stroke-width:2px,color:#311B92;
    classDef storage fill:#E8F5E9,stroke:#43A047,stroke-width:2px,color:#1B5E20;
    classDef init fill:#FFF3E0,stroke:#FB8C00,stroke-width:2px,color:#E65100;

    class Spark compute;
    class Catalog catalog;
    class Storage storage;
    class MC init;
```

---

## Quick Start & Setup

### 1. Start the Docker Environment
```bash
docker-compose up -d
```
This boots up:
- **Iceberg REST Catalog**: `http://localhost:8181`
- **MinIO S3 Storage**: `http://localhost:9000` (Console at `http://localhost:9001` with `admin`/`password`)
- **Spark Master UI**: `http://localhost:8080`

### 2. Build and Deploy the Application
Compile the multi-module project and submit the job using the [`Makefile`](Makefile):

```bash
make deploy
```

> [!TIP]
> The Makefile automatically packages `lakehouse-core` with the shaded `maven-shade-plugin`, copies the uber JAR to the container, and executes `spark-submit` against the Spark Master.

To deploy with custom parameters or master URL:
```bash
make deploy SPARK_MASTER=spark://spark-iceberg:7077
```

### 3. Interactive Spark SQL Shell
Launch a Spark SQL session directly against the Iceberg catalog:

```bash
make spark-sql
```

---

## Spark SQL Examples

### Inspect Namespaces and Tables
```sql
USE demo;
SHOW NAMESPACES;
SHOW TABLES IN default;
```

### Create a Partitioned Iceberg Table
```sql
CREATE TABLE IF NOT EXISTS demo.icebergingestion.taxis (
    vendor_id BIGINT,
    trip_id BIGINT,
    trip_distance FLOAT,
    fare_amount DOUBLE,
    store_and_fwd_flag STRING
)
USING iceberg
PARTITIONED BY (vendor_id);
```

### Insert and Query Records
```sql
INSERT INTO demo.icebergingestion.taxis
VALUES 
    (1, 1000371, 1.8, 15.32, 'N'),
    (2, 1000372, 2.5, 22.15, 'N'),
    (2, 1000373, 0.9, 9.01, 'N'),
    (1, 1000374, 8.4, 42.13, 'Y');

SELECT vendor_id, count(*), avg(fare_amount) 
FROM demo.icebergingestion.taxis 
GROUP BY vendor_id;
```