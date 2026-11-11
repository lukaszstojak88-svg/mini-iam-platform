package pl.stojak.miniiam.audit.domain;

/** Types of audited operations (spec §6.3). Events of any other type are rejected. */
public enum AuditEventType {
    USER_CREATED,
    USER_UPDATED,
    USER_ACTIVATED,
    USER_DEACTIVATED,
    USER_DELETED,
    ROLE_ASSIGNED,
    ROLE_REMOVED
}
