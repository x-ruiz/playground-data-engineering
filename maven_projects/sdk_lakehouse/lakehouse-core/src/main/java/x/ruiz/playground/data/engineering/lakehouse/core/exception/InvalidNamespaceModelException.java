package x.ruiz.playground.data.engineering.lakehouse.core.exception;

/**
 * Thrown when a {@link x.ruiz.playground.data.engineering.lakehouse.core.model.NamespaceModel}
 * fails validation (for example, null catalog, invalid namespace name, or null spec input).
 */
public class InvalidNamespaceModelException extends LakehouseException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message descriptive validation error message
     */
    public InvalidNamespaceModelException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and underlying cause.
     *
     * @param message descriptive validation error message
     * @param cause   original failure cause
     */
    public InvalidNamespaceModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
