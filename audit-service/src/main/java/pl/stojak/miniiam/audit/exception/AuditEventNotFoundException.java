package pl.stojak.miniiam.audit.exception;

public class AuditEventNotFoundException extends RuntimeException {

    public AuditEventNotFoundException(String message) {
        super(message);
    }
}
