package pl.stojak.miniiam.audit.exception;

/** Machine-readable error codes returned in the {@code code} property of problem details (used by the UI). */
public enum ErrorCode {
    VALIDATION_FAILED,
    INVALID_REQUEST,
    AUDIT_EVENT_NOT_FOUND,
    INTERNAL_ERROR
}
