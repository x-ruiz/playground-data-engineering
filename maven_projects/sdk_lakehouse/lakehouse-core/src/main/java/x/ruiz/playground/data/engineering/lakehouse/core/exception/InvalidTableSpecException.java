package x.ruiz.playground.data.engineering.lakehouse.core.exception;

public class InvalidTableSpecException extends LakehouseException {

    public InvalidTableSpecException(String message) {
        super(message);
    }

    public InvalidTableSpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
