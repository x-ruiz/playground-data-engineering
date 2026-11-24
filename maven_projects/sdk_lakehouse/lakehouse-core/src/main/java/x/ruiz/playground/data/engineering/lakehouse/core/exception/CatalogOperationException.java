package x.ruiz.playground.data.engineering.lakehouse.core.exception;

/**
 * Thrown when an unexpected catalog, network, authentication, or storage failure occurs inside
 * a {@link x.ruiz.playground.data.engineering.lakehouse.core.manager.TableManager} or
 * {@link x.ruiz.playground.data.engineering.lakehouse.core.manager.NamespaceManager} implementation.
 */
public class CatalogOperationException extends LakehouseException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message descriptive error message
     */
    public CatalogOperationException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and underlying cause.
     *
     * @param message descriptive error message
     * @param cause   original catalog, network, or storage failure
     */
    public CatalogOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
