package x.ruiz.playground.data.engineering.lakehouse.core.exception;

/**
 * Abstract base unchecked exception for all domain validation, specification parsing,
 * and catalog operation errors raised by {@code lakehouse-core} and its engine adapters.
 */
public abstract class LakehouseException extends RuntimeException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message descriptive error message
     */
    protected LakehouseException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and underlying cause.
     *
     * @param message descriptive error message
     * @param cause   original failure cause
     */
    protected LakehouseException(String message, Throwable cause) {
        super(message, cause);
    }
}
