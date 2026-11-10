package pl.stojak.miniiam.user.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * User profile returned by the API. {@code id} is the local profile id; {@code keycloakId} identifies the
 * account in Keycloak and is used by auth-service endpoints.
 */
public record UserResponse(
        UUID id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
