package pl.stojak.miniiam.user.integration.keycloak;

public class KeycloakNotFoundException extends KeycloakIntegrationException {

    public KeycloakNotFoundException(String operation, Throwable cause) {
        super(operation + ": " + "Account not found in Keycloak (HTTP 404).", cause);
    }
}
