package pl.stojak.miniiam.user.exception;

/** The caller's role allows the endpoint but not this particular target (HTTP 403). */
public class OperationForbiddenException extends RuntimeException {

    private final ErrorCode code;

    public OperationForbiddenException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
