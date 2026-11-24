package x.ruiz.playground.data.engineering.lakehouse.core.exception;

public class InvalidCatalogSpecException extends LakehouseException {

    public InvalidCatalogSpecException(String message) {
        super(message);
    }

    public InvalidCatalogSpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
