package pl.stojak.miniiam.auth.dto.response;

import pl.stojak.miniiam.auth.domain.AppRole;

/**
 * An application role available in the realm.
 *
 * @param assignable whether the role can be assigned/removed via the API ({@code false} for the base role USER)
 */
public record RoleResponse(AppRole name, String description, boolean assignable) {
}
