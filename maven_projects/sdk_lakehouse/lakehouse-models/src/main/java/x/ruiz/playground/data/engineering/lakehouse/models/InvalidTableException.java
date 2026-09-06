package x.ruiz.playground.data.engineering.lakehouse.models;

public class InvalidTableException extends RuntimeException {
    public InvalidTableException(String message) {
        super(message);
    }
}
