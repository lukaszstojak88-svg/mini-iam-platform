package pl.stojak.miniiam.auth.dto.response;

import pl.stojak.miniiam.auth.domain.AppRole;

import java.util.List;

/**
 * Basic identity and access information of the caller, taken from the validated access token.
 *
 * @param userId Keycloak user id (token subject)
 */
public record CurrentUserResponse(
        String userId,
        String username,
        String email,
        String firstName,
        String lastName,
        List<AppRole> roles) {
}
