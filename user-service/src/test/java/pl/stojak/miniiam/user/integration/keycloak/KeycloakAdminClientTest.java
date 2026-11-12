package pl.stojak.miniiam.user.integration.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminClientTest {

    private static final String BASE = "http://keycloak/admin/realms/mini-iam";

    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(builder.build());
    }

    private static NewKeycloakUser newUser() {
        return new NewKeycloakUser("john", "john@example.com", "John", "Doe", "Secret-Pass-123");
    }

    @Test
    void createUserSendsTemporaryPasswordAndReturnsIdFromLocation() {
        server.expect(requestTo(BASE + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.credentials[0].temporary").value(true))
                .andExpect(jsonPath("$.credentials[0].value").value("Secret-Pass-123"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create(BASE + "/users/3f1c-42")));

        assertThat(client.createUser(newUser())).isEqualTo("3f1c-42");
        server.verify();
    }

    @Test
    void conflictIsTranslated() {
        server.expect(requestTo(BASE + "/users")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.createUser(newUser())).isInstanceOf(KeycloakConflictException.class);
    }

    @Test
    void badRequestIsTranslated() {
        server.expect(requestTo(BASE + "/users")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.createUser(newUser())).isInstanceOf(KeycloakBadRequestException.class);
    }

    @Test
    void forbiddenMeansConfigurationProblem() {
        server.expect(requestTo(BASE + "/users")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.createUser(newUser())).isInstanceOf(KeycloakConfigurationException.class);
    }

    @Test
    void serverErrorAndTimeoutMeanUnavailable() {
        server.expect(requestTo(BASE + "/users")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.createUser(newUser())).isInstanceOf(KeycloakUnavailableException.class);

        server.reset();
        server.expect(requestTo(BASE + "/users/u1")).andRespond(withException(new SocketTimeoutException("timeout")));
        assertThatThrownBy(() -> client.findUser("u1")).isInstanceOf(KeycloakUnavailableException.class);
    }

    @Test
    void missingUserIsEmptyAndDeleteIsIdempotent() {
        server.expect(requestTo(BASE + "/users/u1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/users/u1")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findUser("u1")).isEmpty();
        assertThat(client.deleteUser("u1")).isFalse();
    }

    @Test
    void updateProfileSendsOnlyProfileFieldsAndResetsEmailVerification() {
        server.expect(requestTo(BASE + "/users/u1")).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.firstName").value(""))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.enabled").doesNotExist())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.updateProfile("u1", "new@example.com", null, "Doe", true);
        server.verify();
    }

    @Test
    void updateProfileWithoutEmailChangeKeepsVerificationFlag() {
        server.expect(requestTo(BASE + "/users/u1")).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.emailVerified").doesNotExist())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.updateProfile("u1", "same@example.com", "A", "B", false);
        server.verify();
    }

    @Test
    void setEnabledSendsOnlyTheFlag() {
        server.expect(requestTo(BASE + "/users/u1")).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.setEnabled("u1", false);
        server.verify();
    }

    @Test
    void findsUserIdByExactUsername() {
        server.expect(requestTo(BASE + "/users?username=john&exact=true&briefRepresentation=true"))
                .andRespond(withSuccess("[{\"id\":\"kc-1\",\"username\":\"john\",\"enabled\":true}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.findUserIdByUsername("john")).contains("kc-1");
    }

    @Test
    void roleMembersArePagedUntilShortPage() {
        StringBuilder full = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            full.append(i == 0 ? "" : ",").append("{\"id\":\"a").append(i).append("\",\"enabled\":true}");
        }
        full.append("]");
        server.expect(requestTo(BASE + "/roles/ADMIN/users?first=0&max=100&briefRepresentation=true"))
                .andRespond(withSuccess(full.toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/roles/ADMIN/users?first=100&max=100&briefRepresentation=true"))
                .andRespond(withSuccess("[{\"id\":\"last\",\"enabled\":false}]", MediaType.APPLICATION_JSON));

        assertThat(client.getRealmRoleMembers("ADMIN")).hasSize(101);
    }

    @Test
    void effectiveRealmRolesAreReturnedByName() {
        server.expect(requestTo(BASE + "/users/u1/role-mappings/realm/composite"))
                .andRespond(withSuccess("[{\"name\":\"ADMIN\"},{\"name\":\"USER\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.getEffectiveRealmRoles("u1")).containsExactly("ADMIN", "USER");
    }

    @Test
    void missingLocationHeaderIsAnIntegrationError() {
        server.expect(requestTo(BASE + "/users"))
                .andRespond(withStatus(HttpStatus.CREATED).headers(new HttpHeaders()));

        assertThatThrownBy(() -> client.createUser(newUser())).isInstanceOf(KeycloakConfigurationException.class);
    }
}
