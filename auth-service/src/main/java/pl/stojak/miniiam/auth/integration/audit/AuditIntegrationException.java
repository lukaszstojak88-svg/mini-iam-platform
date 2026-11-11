package pl.stojak.miniiam.auth.integration.audit;

/** audit-service rejected the request (4xx) or the service token could not be obtained (HTTP 502 for the caller). */
public class AuditIntegrationException extends RuntimeException {

    public AuditIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
