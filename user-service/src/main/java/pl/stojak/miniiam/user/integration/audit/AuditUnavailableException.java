package pl.stojak.miniiam.user.integration.audit;

/** audit-service cannot be reached, timed out or failed with 5xx (internal to {@link AuditEventPublisher}). */
class AuditUnavailableException extends RuntimeException {

    AuditUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
