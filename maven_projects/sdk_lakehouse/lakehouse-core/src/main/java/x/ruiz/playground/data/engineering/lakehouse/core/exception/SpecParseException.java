package x.ruiz.playground.data.engineering.lakehouse.core.exception;

public class SpecParseException extends LakehouseException {

    public SpecParseException(String message) {
        super(message);
    }

    public SpecParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
