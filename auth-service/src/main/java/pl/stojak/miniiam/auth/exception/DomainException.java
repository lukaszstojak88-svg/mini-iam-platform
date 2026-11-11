package pl.stojak.miniiam.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * A domain rule rejected the request. Carries the HTTP status and a stable {@link ErrorCode}; the message is
 * generic and safe to return to the client.
 */
public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    private DomainException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** HTTP 400 - the request is invalid in the domain sense (e.g. a role that cannot be assigned). */
    public static DomainException badRequest(ErrorCode code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }

    /** HTTP 403 - the caller's role allows the endpoint but not this particular target. */
    public static DomainException forbidden(ErrorCode code, String message) {
        return new DomainException(HttpStatus.FORBIDDEN, code, message);
    }

    /** HTTP 404 - user, role or role assignment does not exist. */
    public static DomainException notFound(ErrorCode code, String message) {
        return new DomainException(HttpStatus.NOT_FOUND, code, message);
    }

    /** HTTP 409 - the operation conflicts with the current state (duplicate, last administrator). */
    public static DomainException conflict(ErrorCode code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }
}
