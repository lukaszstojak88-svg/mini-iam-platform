package pl.stojak.miniiam.auth.integration.keycloak;

public class KeycloakNotFoundException extends KeycloakIntegrationException {

    public KeycloakNotFoundException(String operation, Throwable cause) {
        super(operation + ": " + "User or role not found in Keycloak (HTTP 404).", cause);
    }
}
