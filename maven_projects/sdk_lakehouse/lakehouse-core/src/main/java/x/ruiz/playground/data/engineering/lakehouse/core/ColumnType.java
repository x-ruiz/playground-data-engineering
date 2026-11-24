package x.ruiz.playground.data.engineering.lakehouse.core;

import x.ruiz.playground.data.engineering.lakehouse.core.exception.InvalidTableSpecException;

import java.util.Locale;

public enum ColumnType {
    STRING,
    INT,
    BIGINT,
    DOUBLE,
    BOOLEAN,
    DATE,
    TIMESTAMP,
    DECIMAL;

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
