package pl.stojak.miniiam.user.integration.audit;

/** audit-service rejected the request (4xx) or the service token was rejected (internal to {@link AuditEventPublisher}). */
class AuditIntegrationException extends RuntimeException {

    AuditIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
