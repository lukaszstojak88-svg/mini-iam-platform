package pl.stojak.miniiam.auth.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of Keycloak's UserRepresentation used by auth-service.
 *
 * @param serviceAccountClientId set only for service accounts of confidential clients
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(String id, String username, boolean enabled, String serviceAccountClientId) {

    public boolean isServiceAccount() {
        return serviceAccountClientId != null;
    }
}
