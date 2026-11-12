package pl.stojak.miniiam.user.dto.request;

import org.junit.jupiter.api.Test;
import pl.stojak.miniiam.user.integration.keycloak.NewKeycloakUser;

import static org.assertj.core.api.Assertions.assertThat;

/** Records carrying the temporary password must not expose it via toString() (e.g. in log arguments). */
class CreateUserRequestTest {

    private static final String PASSWORD = "Secret-Pass-123";

    @Test
    void toStringDoesNotContainTemporaryPasswordOrPersonalData() {
        CreateUserRequest request = new CreateUserRequest("john", "john@example.com", "John", "Doe", PASSWORD);

        assertThat(request.toString()).contains("john").doesNotContain(PASSWORD, "john@example.com");
    }

    @Test
    void keycloakUserToStringDoesNotContainTemporaryPassword() {
        NewKeycloakUser user = new NewKeycloakUser("john", "john@example.com", "John", "Doe", PASSWORD);

        assertThat(user.toString()).contains("john").doesNotContain(PASSWORD);
    }
}
