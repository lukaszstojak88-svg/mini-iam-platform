package pl.stojak.miniiam.user.exception;

/** Machine-readable error codes returned in the {@code code} property of problem details (used by the UI). */
public enum ErrorCode {
    VALIDATION_FAILED,
    INVALID_REQUEST,
    INVALID_USER_DATA,
    USER_NOT_FOUND,
    USERNAME_OR_EMAIL_TAKEN,
    LAST_ADMIN,
    ADMIN_ACCOUNT_PROTECTED,
    USER_SYNC_FAILED,
    KEYCLOAK_UNAVAILABLE,
    KEYCLOAK_INTEGRATION_ERROR,
    INTERNAL_ERROR
}
