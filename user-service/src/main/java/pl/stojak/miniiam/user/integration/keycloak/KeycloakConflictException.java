package pl.stojak.miniiam.user.integration.keycloak;

public class KeycloakConflictException extends KeycloakIntegrationException {

    public KeycloakConflictException(String operation, Throwable cause) {
        super(operation + ": " + "Username or e-mail already exists in Keycloak (HTTP 409).", cause);
    }
}
