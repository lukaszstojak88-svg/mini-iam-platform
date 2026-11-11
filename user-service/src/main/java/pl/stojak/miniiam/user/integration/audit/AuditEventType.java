package pl.stojak.miniiam.user.integration.audit;

/** Audit event types sent by user-service (subset of audit-service's types, spec §6.3). */
public enum AuditEventType {
    USER_CREATED,
    USER_UPDATED,
    USER_ACTIVATED,
    USER_DEACTIVATED,
    USER_DELETED
}
