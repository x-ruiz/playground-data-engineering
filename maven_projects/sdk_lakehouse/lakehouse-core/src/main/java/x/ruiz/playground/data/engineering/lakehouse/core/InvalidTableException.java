package x.ruiz.playground.data.engineering.lakehouse.core;

public class InvalidTableException extends RuntimeException {
    public InvalidTableException(String message) {
        super(message);
    }
}
