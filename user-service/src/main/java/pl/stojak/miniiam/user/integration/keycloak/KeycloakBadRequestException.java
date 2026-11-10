package pl.stojak.miniiam.user.integration.keycloak;

public class KeycloakBadRequestException extends KeycloakIntegrationException {

    public KeycloakBadRequestException(String operation, Throwable cause) {
        super(operation + ": " + "Keycloak rejected the request data (HTTP 400).", cause);
    }
}
