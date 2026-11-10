package pl.stojak.miniiam.user.service;

import java.util.Set;

/**
 * Caller of an operation: Keycloak user id (token subject) and application roles (without the ROLE_ prefix).
 */
public record Actor(String keycloakId, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
