package pl.stojak.miniiam.auth.exception;

/** Machine-readable error codes returned in the {@code code} property of problem details (used by the UI). */
public enum ErrorCode {
    VALIDATION_FAILED,
    INVALID_REQUEST,
    USER_NOT_FOUND,
    ROLE_NOT_FOUND,
    RESOURCE_NOT_FOUND,
    ROLE_NOT_ASSIGNABLE,
    ROLE_NOT_ASSIGNED,
    ROLE_ALREADY_ASSIGNED,
    SELF_ROLE_CHANGE,
    SERVICE_ACCOUNT_PROTECTED,
    LAST_ADMIN,
    KEYCLOAK_UNAVAILABLE,
    KEYCLOAK_INTEGRATION_ERROR,
    AUDIT_UNAVAILABLE,
    AUDIT_INTEGRATION_ERROR,
    INTERNAL_ERROR
}
