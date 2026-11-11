package pl.stojak.miniiam.audit.exception;

/** Request rejected by a rule that bean validation cannot express (sort property, date range, details size). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
