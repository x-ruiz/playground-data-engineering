package x.ruiz.playground.data.engineering.lakehouse.protobuf;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Converts Protocol Buffers message descriptors into Apache Iceberg Schemas.
 * Aligns Protobuf permanent field numbers directly with Iceberg field-ids
 * to guarantee deterministic schema evolution across versions.
 */
public class ProtoToIcebergSchemaConverter {
    private static final Logger logger = LogManager.getLogger(ProtoToIcebergSchemaConverter.class);

    private static final String GOOGLE_TIMESTAMP = "google.protobuf.Timestamp";
    private static final String GOOGLE_DURATION = "google.protobuf.Duration";
    private static final String GOOGLE_DATE = "google.type.Date";
    private static final String GOOGLE_TIME_OF_DAY = "google.type.TimeOfDay";

    /**
     * Converts a Protobuf Descriptor to an Iceberg Schema.
     *
     * @param descriptor the Protobuf message descriptor
     * @return the corresponding Iceberg Schema
     */
    public Schema toIcebergSchema(Descriptor descriptor) {
        AtomicInteger idGenerator = new AtomicInteger(10000); // For nested elements, list items, and maps
        List<Types.NestedField> fields = new ArrayList<>();

        for (FieldDescriptor field : descriptor.getFields()) {
            int fieldId = field.getNumber(); // Permanent Protobuf tag becomes Iceberg field-id
            Type type = convertFieldType(field, idGenerator);
            boolean isRequired = field.isRequired();
            String doc = null;

            if (isRequired) {
                fields.add(Types.NestedField.required(fieldId, field.getName(), type, doc));
            } else {
                fields.add(Types.NestedField.optional(fieldId, field.getName(), type, doc));
            }
        }

        return new Schema(fields);
    }

    private Type convertFieldType(FieldDescriptor field, AtomicInteger idGen) {
        if (field.isMapField()) {
            Descriptor mapEntry = field.getMessageType();
            FieldDescriptor keyField = mapEntry.findFieldByName("key");
            FieldDescriptor valueField = mapEntry.findFieldByName("value");

            int keyId = idGen.getAndIncrement();
            int valId = idGen.getAndIncrement();
            Type keyType = convertFieldType(keyField, idGen);
            Type valType = convertFieldType(valueField, idGen);

            return Types.MapType.ofOptional(keyId, valId, keyType, valType);
        }

        if (field.isRepeated()) {
            int elementId = idGen.getAndIncrement();
            Type elementType = convertScalarOrMessageType(field, idGen);
            return Types.ListType.ofOptional(elementId, elementType);
        }

        return convertScalarOrMessageType(field, idGen);
    }

    private Type convertScalarOrMessageType(FieldDescriptor field, AtomicInteger idGen) {
        return switch (field.getType()) {
            case INT32, SINT32, SFIXED32 -> Types.IntegerType.get();
            case INT64, SINT64, SFIXED64 -> Types.LongType.get();
            case UINT32, FIXED32 -> Types.LongType.get(); // Promoted to Long to prevent 32-bit unsigned overflow
            case UINT64, FIXED64 -> Types.DecimalType.of(20, 0); // Preserves 64-bit unsigned precision
            case FLOAT -> Types.FloatType.get();
            case DOUBLE -> Types.DoubleType.get();
            case BOOL -> Types.BooleanType.get();
            case STRING -> Types.StringType.get();
            case BYTES -> Types.BinaryType.get();
            case ENUM -> Types.StringType.get(); // Enums stored as String to preserve readability and evolution
            case MESSAGE -> convertMessageType(field.getMessageType(), idGen);
            default -> throw new UnsupportedOperationException("Unsupported Protobuf field type: " + field.getType());
        };
    }

    private Type convertMessageType(Descriptor messageDescriptor, AtomicInteger idGen) {
        String fullName = messageDescriptor.getFullName();

        // Handle Google Well-Known Types (WKTs)
        if (GOOGLE_TIMESTAMP.equals(fullName)) {
            return Types.TimestampType.withZone();
        } else if (GOOGLE_DURATION.equals(fullName)) {
            return Types.LongType.get(); // nanoseconds
        } else if (GOOGLE_DATE.equals(fullName)) {
            return Types.DateType.get();
        } else if (GOOGLE_TIME_OF_DAY.equals(fullName)) {
            return Types.TimeType.get();
        }

        // Handle wrapper types
        if ("google.protobuf.Int32Value".equals(fullName)) return Types.IntegerType.get();
        if ("google.protobuf.Int64Value".equals(fullName)) return Types.LongType.get();
        if ("google.protobuf.StringValue".equals(fullName)) return Types.StringType.get();
        if ("google.protobuf.DoubleValue".equals(fullName)) return Types.DoubleType.get();
        if ("google.protobuf.FloatValue".equals(fullName)) return Types.FloatType.get();
        if ("google.protobuf.BoolValue".equals(fullName)) return Types.BooleanType.get();

        // Recursive StructType for nested message
        List<Types.NestedField> nestedFields = new ArrayList<>();
        for (FieldDescriptor nestedField : messageDescriptor.getFields()) {
            int nestedId = idGen.getAndIncrement();
            Type nestedType = convertFieldType(nestedField, idGen);
            nestedFields.add(Types.NestedField.optional(nestedId, nestedField.getName(), nestedType));
        }

        return Types.StructType.of(nestedFields);
    }
}
