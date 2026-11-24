package x.ruiz.playground.data.engineering.lakehouse.core.exception;

/**
 * Thrown when a {@link x.ruiz.playground.data.engineering.lakehouse.core.model.TableModel},
 * {@link x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnModel}, or
 * {@link x.ruiz.playground.data.engineering.lakehouse.core.model.ColumnType} fails validation.
 */
public class InvalidTableModelException extends LakehouseException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message descriptive validation error message
     */
    public InvalidTableModelException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and underlying cause.
     *
     * @param message descriptive validation error message
     * @param cause   original failure cause
     */
    public InvalidTableModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
