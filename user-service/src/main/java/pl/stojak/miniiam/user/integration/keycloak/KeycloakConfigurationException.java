package pl.stojak.miniiam.user.integration.keycloak;

public class KeycloakConfigurationException extends KeycloakIntegrationException {

    public KeycloakConfigurationException(String operation, Throwable cause) {
        super(operation + ": " + "Keycloak rejected the technical client (authorization or configuration problem).", cause);
    }
}
