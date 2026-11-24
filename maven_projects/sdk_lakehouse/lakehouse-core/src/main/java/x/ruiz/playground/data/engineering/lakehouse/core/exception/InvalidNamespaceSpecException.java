package x.ruiz.playground.data.engineering.lakehouse.core.exception;

/**
 * Thrown when a {@link x.ruiz.playground.data.engineering.lakehouse.core.spec.NamespaceSpec}
 * fails validation (for example, null catalog, invalid namespace name, or null spec input).
 */
public class InvalidNamespaceSpecException extends LakehouseException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message descriptive validation error message
     */
    public InvalidNamespaceSpecException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and underlying cause.
     *
     * @param message descriptive validation error message
     * @param cause   original failure cause
     */
    public InvalidNamespaceSpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
