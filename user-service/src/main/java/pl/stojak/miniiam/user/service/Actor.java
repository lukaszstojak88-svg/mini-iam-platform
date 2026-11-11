package pl.stojak.miniiam.user.service;

import java.util.Set;

/**
 * Caller of an operation: Keycloak user id (token subject), username ({@code preferred_username} claim, may be
 * null for tokens without it) and application roles (without the ROLE_ prefix).
 */
public record Actor(String keycloakId, String username, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
