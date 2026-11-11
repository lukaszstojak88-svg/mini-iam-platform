package pl.stojak.miniiam.auth.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of Keycloak's UserRepresentation used by auth-service. Service accounts cannot be recognised from it:
 * {@code GET /users/{id}} does not return {@code serviceAccountClientId}; they are marked by the SERVICE realm role.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(String id, String username, boolean enabled) {
}
