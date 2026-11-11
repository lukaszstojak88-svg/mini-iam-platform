package pl.stojak.miniiam.auth.integration.audit;

/** Audit event types sent by auth-service (subset of audit-service's types, spec §6.3). */
public enum AuditEventType {
    ROLE_ASSIGNED,
    ROLE_REMOVED
}
