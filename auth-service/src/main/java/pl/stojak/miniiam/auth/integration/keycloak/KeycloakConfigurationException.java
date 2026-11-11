package pl.stojak.miniiam.auth.integration.keycloak;

/**
 * Keycloak rejected the technical client or the request itself (401/403, or an unexpected 4xx such as 400/409).
 * With validated input this indicates a configuration problem, never a user error.
 */
public class KeycloakConfigurationException extends KeycloakIntegrationException {

    public KeycloakConfigurationException(String operation, Throwable cause) {
        super(operation + ": " + "Keycloak rejected the request of the technical client (authorization or configuration problem).", cause);
    }
}
