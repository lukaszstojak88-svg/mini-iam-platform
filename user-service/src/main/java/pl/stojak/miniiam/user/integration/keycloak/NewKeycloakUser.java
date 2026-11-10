package pl.stojak.miniiam.user.integration.keycloak;

/**
 * Data of an account to create. {@code temporaryPassword} is sent to Keycloak only and never stored or logged.
 */
public record NewKeycloakUser(String username, String email, String firstName, String lastName,
                              String temporaryPassword) {

    @Override
    public String toString() {
        return "NewKeycloakUser[username=" + username + "]";
    }
}
