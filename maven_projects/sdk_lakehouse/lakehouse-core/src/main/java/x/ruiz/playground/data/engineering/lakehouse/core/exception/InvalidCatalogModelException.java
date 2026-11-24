package x.ruiz.playground.data.engineering.lakehouse.core.exception;

/**
 * Thrown when a {@link x.ruiz.playground.data.engineering.lakehouse.core.model.CatalogModel}
 * fails validation (for example, a null, blank, or malformed catalog name).
 */
public class InvalidCatalogModelException extends LakehouseException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message descriptive validation error message
     */
    public InvalidCatalogModelException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and underlying cause.
     *
     * @param message descriptive validation error message
     * @param cause   original failure cause
     */
    public InvalidCatalogModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
