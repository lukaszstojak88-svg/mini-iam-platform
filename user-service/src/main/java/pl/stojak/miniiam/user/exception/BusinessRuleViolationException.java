package pl.stojak.miniiam.user.exception;

/** A domain rule forbids the operation in the current state (HTTP 409), e.g. removing the last active admin. */
public class BusinessRuleViolationException extends RuntimeException {

    private final ErrorCode code;

    public BusinessRuleViolationException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
