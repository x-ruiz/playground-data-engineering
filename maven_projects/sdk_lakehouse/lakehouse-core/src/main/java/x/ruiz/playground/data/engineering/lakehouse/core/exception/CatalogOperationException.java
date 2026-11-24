package x.ruiz.playground.data.engineering.lakehouse.core.exception;

public class CatalogOperationException extends LakehouseException {

    public CatalogOperationException(String message) {
        super(message);
    }

    public CatalogOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
