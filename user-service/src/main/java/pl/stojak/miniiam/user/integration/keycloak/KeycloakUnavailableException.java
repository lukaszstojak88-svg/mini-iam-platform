package pl.stojak.miniiam.user.integration.keycloak;

public class KeycloakUnavailableException extends KeycloakIntegrationException {

    public KeycloakUnavailableException(String operation, Throwable cause) {
        super(operation + ": " + "Keycloak is unavailable.", cause);
    }
}
