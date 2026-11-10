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
        String name = authority.substring(AUTHORITY_PREFIX.length());
        return Arrays.stream(values()).filter(role -> role.name().equals(name)).findFirst();
    }
}
