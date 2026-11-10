package pl.stojak.miniiam.user.integration.keycloak;

/** Base class of errors returned by (or while calling) the Keycloak Admin REST API. */
public abstract class KeycloakIntegrationException extends RuntimeException {

    protected KeycloakIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
