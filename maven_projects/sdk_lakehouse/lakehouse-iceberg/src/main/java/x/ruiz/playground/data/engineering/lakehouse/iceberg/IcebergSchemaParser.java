package x.ruiz.playground.data.engineering.lakehouse.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IcebergSchemaParser {
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("decimal\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private IcebergSchemaParser() {}

    /**
     * Parses a comma-separated SQL column definition string into an Iceberg Schema.
     * Example: "id BIGINT, name STRING, fare_amount DOUBLE, ts TIMESTAMP"
     *
     * @param schemaString DDL column definition string
     * @return Iceberg Schema with deterministic field-ids (1-based)
     */
    public static Schema parse(String schemaString) {
        if (schemaString == null || schemaString.trim().isEmpty()) {
            return new Schema();
        }

        List<Types.NestedField> fields = new ArrayList<>();
        List<String> columnDefs = splitColumnDefinitions(schemaString);
        int fieldId = 1;

        for (String colDef : columnDefs) {
            String trimmed = colDef.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Split into column name and column type (and optional NULL / NOT NULL)
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid column definition: '" + trimmed + "'. Expected '<name> <type>'");
            }

            String colName = parts[0].trim();
            String remainder = parts[1].trim();

            boolean isRequired = false;
            String typeStr = remainder;

            if (remainder.toUpperCase(Locale.ROOT).endsWith("NOT NULL")) {
                isRequired = true;
                typeStr = remainder.substring(0, remainder.toUpperCase(Locale.ROOT).lastIndexOf("NOT NULL")).trim();
            } else if (remainder.toUpperCase(Locale.ROOT).endsWith("NULL")) {
                typeStr = remainder.substring(0, remainder.toUpperCase(Locale.ROOT).lastIndexOf("NULL")).trim();
            }

            Type type = parseType(typeStr);
            if (isRequired) {
                fields.add(Types.NestedField.required(fieldId++, colName, type));
            } else {
                fields.add(Types.NestedField.optional(fieldId++, colName, type));
            }
        }

        return new Schema(fields);
    }

    public static Type parseType(String typeString) {
        String normalized = typeString.trim().toLowerCase(Locale.ROOT);

        Matcher decimalMatcher = DECIMAL_PATTERN.matcher(normalized);
        if (decimalMatcher.matches()) {
            int precision = Integer.parseInt(decimalMatcher.group(1));
            int scale = Integer.parseInt(decimalMatcher.group(2));
            return Types.DecimalType.of(precision, scale);
        }

        return switch (normalized) {
            case "boolean", "bool" -> Types.BooleanType.get();
            case "int", "integer" -> Types.IntegerType.get();
            case "bigint", "long" -> Types.LongType.get();
            case "float" -> Types.FloatType.get();
            case "double" -> Types.DoubleType.get();
            case "string", "varchar", "text" -> Types.StringType.get();
            case "date" -> Types.DateType.get();
            case "time" -> Types.TimeType.get();
            case "timestamp", "timestamptz", "timestamp_tz" -> Types.TimestampType.withZone();
            case "timestamp_ntz" -> Types.TimestampType.withoutZone();
            case "binary", "bytes" -> Types.BinaryType.get();
            case "uuid" -> Types.UUIDType.get();
            default -> throw new IllegalArgumentException("Unsupported Iceberg data type: '" + typeString + "'");
        };
    }

    private static List<String> splitColumnDefinitions(String schemaString) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (char c : schemaString.toCharArray()) {
            if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }

        return result;
    }
}
