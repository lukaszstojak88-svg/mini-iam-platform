package pl.stojak.miniiam.user.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Subset of Keycloak's UserRepresentation used by user-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        List<String> requiredActions) {
}
