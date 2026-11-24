package x.ruiz.playground.data.engineering.lakehouse.core.spec;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;

import java.util.Locale;

/**
 * Closed set of engine-agnostic canonical column types supported by {@code lakehouse-core}.
 *
 * <p>Engine adapter modules (such as {@code lakehouse-iceberg}) map each value to its
 * corresponding engine-native schema type.
 */
public enum ColumnType {
    /** Variable-length UTF-8 character string. */
    STRING,
    /** 32-bit signed integer. */
    INT,
    /** 64-bit signed integer. */
    BIGINT,
    /** 64-bit IEEE 754 floating-point number. */
    DOUBLE,
    /** Logical true/false value. */
    BOOLEAN,
    /** Calendar date without time-of-day or timezone. */
    DATE,
    /** Point-in-time timestamp. */
    TIMESTAMP,
    /** Fixed-point exact decimal number (default precision/scale defined by engine adapters). */
    DECIMAL;

    /**
     * Parses a raw column type string case-insensitively after trimming surrounding whitespace.
     *
     * @param raw raw type name (for example, {@code "date"} or {@code " BIGINT "})
     * @return the matching canonical {@link ColumnType}
     * @throws InvalidTableSpecException if {@code raw} is null, blank, or does not match a supported type
     */
    public static ColumnType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidTableSpecException("Column type must not be null or blank");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return ColumnType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidTableSpecException("Unsupported column type: '" + raw + "'", e);
        }
    }
}
