package pl.stojak.miniiam.auth.integration.audit;

/** audit-service cannot be reached, timed out or failed with 5xx (HTTP 503 for the caller). */
public class AuditUnavailableException extends RuntimeException {

    public AuditUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
