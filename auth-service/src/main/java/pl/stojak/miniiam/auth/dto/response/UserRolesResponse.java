package pl.stojak.miniiam.auth.dto.response;

import pl.stojak.miniiam.auth.domain.AppRole;

import java.util.List;

/**
 * Effective application roles of a user, as currently stored in Keycloak (technical and built-in roles omitted).
 *
 * @param userId Keycloak user id
 */
public record UserRolesResponse(String userId, List<AppRole> roles) {
}
