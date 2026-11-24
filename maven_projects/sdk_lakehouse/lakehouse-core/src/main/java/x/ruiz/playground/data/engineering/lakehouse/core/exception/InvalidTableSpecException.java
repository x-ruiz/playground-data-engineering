package x.ruiz.playground.data.engineering.lakehouse.core.exception;

/**
 * Thrown when a {@link x.ruiz.playground.data.engineering.lakehouse.core.spec.TableSpec},
 * {@link x.ruiz.playground.data.engineering.lakehouse.core.spec.ColumnSpec}, or
 * {@link x.ruiz.playground.data.engineering.lakehouse.core.spec.ColumnType} fails validation.
 */
public class InvalidTableSpecException extends LakehouseException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message descriptive validation error message
     */
    public InvalidTableSpecException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and underlying cause.
     *
     * @param message descriptive validation error message
     * @param cause   original failure cause
     */
    public InvalidTableSpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
