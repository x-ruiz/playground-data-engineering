package x.ruiz.playground.data.engineering.lakehouse.core.exception;

public class InvalidNamespaceSpecException extends LakehouseException {

    public InvalidNamespaceSpecException(String message) {
        super(message);
    }

    public InvalidNamespaceSpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
