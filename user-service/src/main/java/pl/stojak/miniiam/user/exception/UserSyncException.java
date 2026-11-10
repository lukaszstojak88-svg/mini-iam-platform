package pl.stojak.miniiam.user.exception;

/**
 * Keycloak and the local profile could not be kept consistent (partial failure, spec §6.1). The details for
 * reconciliation are logged; the API response stays generic.
 */
public class UserSyncException extends RuntimeException {

    public UserSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
