package pl.stojak.miniiam.auth.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of Keycloak's RoleRepresentation. The id is needed by the role-mapping endpoints and is never
 * returned by auth-service's own API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRole(String id, String name, String description) {
}
