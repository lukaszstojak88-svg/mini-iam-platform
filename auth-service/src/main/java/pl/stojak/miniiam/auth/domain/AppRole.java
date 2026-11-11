package pl.stojak.miniiam.auth.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Application roles (spec §2). These are fixed domain constants, not values created in the UI.
 * The technical SERVICE role is intentionally not part of this enum.
 */
public enum AppRole {
    ADMIN,
    MANAGER,
    USER,
    AUDITOR;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    public static Optional<AppRole> fromAuthority(String authority) {
        if (authority == null || !authority.startsWith(AUTHORITY_PREFIX)) {
            return Optional.empty();
        }
        return fromName(authority.substring(AUTHORITY_PREFIX.length()));
    }

    /** Exact (case-sensitive) match of a Keycloak realm role name. */
    public static Optional<AppRole> fromName(String name) {
        return Arrays.stream(values()).filter(role -> role.name().equals(name)).findFirst();
    }

    /** USER is the base role granted by Keycloak default roles; it cannot be assigned or removed via the API. */
    public boolean isAssignable() {
        return this != USER;
    }
}
