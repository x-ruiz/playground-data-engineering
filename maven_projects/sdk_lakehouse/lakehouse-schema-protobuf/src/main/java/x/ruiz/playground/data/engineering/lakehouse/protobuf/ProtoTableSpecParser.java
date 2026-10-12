package x.ruiz.playground.data.engineering.lakehouse.protobuf;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableProperties;
import x.ruiz.playground.data.engineering.lakehouse.core.TableSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.WorkloadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Parses Protobuf message descriptors into concrete {@link TableSpec} definitions
 * tailored for batch or streaming Iceberg table creation.
 */
public class ProtoTableSpecParser {
    private final ProtoToIcebergSchemaConverter schemaConverter;

    public ProtoTableSpecParser() {
        this.schemaConverter = new ProtoToIcebergSchemaConverter();
    }

    public ProtoTableSpecParser(ProtoToIcebergSchemaConverter schemaConverter) {
        this.schemaConverter = Objects.requireNonNull(schemaConverter, "schemaConverter must not be null");
    }

    /**
     * Parses a Protobuf Descriptor into a TableSpec with the given WorkloadMode.
     *
     * @param descriptor the Protobuf message descriptor
     * @param mode the workload execution mode (BATCH or STREAMING)
     * @return populated TableSpec
     */
    public TableSpec parse(Descriptor descriptor, WorkloadMode mode) {
        Objects.requireNonNull(descriptor, "Descriptor must not be null");
        Objects.requireNonNull(mode, "WorkloadMode must not be null");

        String tableName = descriptor.getName().toLowerCase(Locale.ROOT);
        String namespace = descriptor.getFile().getPackage().isEmpty()
                ? "default"
                : descriptor.getFile().getPackage().replace('.', '_');

        Schema schema = schemaConverter.toIcebergSchema(descriptor);

        // Scan for potential partition fields (timestamp or id columns)
        List<String> partitionColumns = new ArrayList<>();
        List<String> sortColumns = new ArrayList<>();

        for (FieldDescriptor field : descriptor.getFields()) {
            String fieldName = field.getName().toLowerCase(Locale.ROOT);
            if (field.getType() == FieldDescriptor.Type.MESSAGE &&
                    "google.protobuf.Timestamp".equals(field.getMessageType().getFullName())) {
                partitionColumns.add("day(" + field.getName() + ")");
                sortColumns.add(field.getName());
            } else if (fieldName.endsWith("_time") || fieldName.endsWith("_at") || fieldName.equals("ts")) {
                if (field.getType() == FieldDescriptor.Type.INT64) {
                    partitionColumns.add(field.getName());
                }
            }
        }

        // Configure table properties
        Map<String, String> properties = new HashMap<>();
        properties.put(TableProperties.FORMAT_VERSION, "2");
        properties.put("write.format.default", "parquet");

        if (mode == WorkloadMode.STREAMING) {
            properties.put("write.target-file-size-bytes", "134217728"); // 128 MB
            properties.put("write.distribution-mode", "fanout");
            properties.put("commit.manifest.min-count-to-merge", "4");
            properties.put("history.expire.max-snapshot-age-ms", "86400000"); // 1 day
        } else {
            properties.put("write.target-file-size-bytes", "536870912"); // 512 MB
            properties.put("write.distribution-mode", "hash");
        }

        return TableSpec.builder(tableName)
                .namespace(namespace)
                .schema(schema.toString())
                .partitionBy(partitionColumns)
                .sortBy(sortColumns)
                .properties(properties)
                .mode(mode)
                .build();
    }
}
