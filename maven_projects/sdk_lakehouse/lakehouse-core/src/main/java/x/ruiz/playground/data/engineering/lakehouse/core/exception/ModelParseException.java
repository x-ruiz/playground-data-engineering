package x.ruiz.playground.data.engineering.lakehouse.core.exception;

/**
 * Thrown when reading or deserializing a declarative specification file fails due to a null path,
 * missing/unreadable file, empty content, malformed syntax, or unknown properties.
 */
public class ModelParseException extends LakehouseException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message descriptive parse error message
     */
    public ModelParseException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and underlying cause.
     *
     * @param message descriptive parse error message
     * @param cause   original I/O or serialization failure
     */
    public ModelParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
