package pl.stojak.miniiam.auth.integration.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private static final KeycloakRole MANAGER = new KeycloakRole("r-1", "MANAGER", "Manager");

    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(builder.build());
    }

    @Test
    void readsUser() {
        server.expect(requestTo(BASE + "/users/u1")).andRespond(withSuccess("""
                {"id":"u1","username":"service-account-x","enabled":true,"email":"a@b"}""",
                MediaType.APPLICATION_JSON));

        KeycloakUser user = client.findUser("u1").orElseThrow();
        assertThat(user.enabled()).isTrue();
    }

    @Test
    void missingUserAndRoleAreEmpty() {
        server.expect(requestTo(BASE + "/users/u1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/roles/AUDITOR")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findUser("u1")).isEmpty();
        assertThat(client.findRealmRole("AUDITOR")).isEmpty();
    }

    @Test
    void readsDirectAndEffectiveRoleMappings() {
        server.expect(requestTo(BASE + "/users/u1/role-mappings/realm"))
                .andRespond(withSuccess("[{\"id\":\"r-1\",\"name\":\"MANAGER\",\"composite\":false}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/users/u1/role-mappings/realm/composite?briefRepresentation=true"))
                .andRespond(withSuccess("[{\"id\":\"r-1\",\"name\":\"MANAGER\"},{\"id\":\"r-2\",\"name\":\"USER\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getDirectRealmRoles("u1")).extracting(KeycloakRole::id, KeycloakRole::name)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("r-1", "MANAGER"));
        assertThat(client.getEffectiveRealmRoles("u1")).extracting(KeycloakRole::name).containsExactly("MANAGER", "USER");
    }

    @Test
    void addAndRemoveSendRoleReference() {
        server.expect(requestTo(BASE + "/users/u1/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].id").value("r-1"))
                .andExpect(jsonPath("$[0].name").value("MANAGER"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(BASE + "/users/u1/role-mappings/realm"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(jsonPath("$[0].id").value("r-1"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.addRealmRole("u1", MANAGER);
        client.removeRealmRole("u1", MANAGER);
        server.verify();
    }

    @Test
    void roleMembersAreReadPageByPage() {
        String fullPage = IntStream.range(0, 100)
                .mapToObj(i -> "{\"id\":\"u" + i + "\",\"username\":\"u" + i + "\",\"enabled\":true}")
                .collect(Collectors.joining(",", "[", "]"));
        server.expect(requestTo(BASE + "/roles/ADMIN/users?first=0&max=100&briefRepresentation=true"))
                .andRespond(withSuccess(fullPage, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/roles/ADMIN/users?first=100&max=100&briefRepresentation=true"))
                .andRespond(withSuccess("[{\"id\":\"last\",\"username\":\"last\",\"enabled\":false}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getRealmRoleMembers("ADMIN")).hasSize(101);
        server.verify();
    }

    @Test
    void clientErrorsMeanConfigurationProblem() {
        server.expect(requestTo(BASE + "/roles?briefRepresentation=true")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.listRealmRoles()).isInstanceOf(KeycloakConfigurationException.class);

        server.reset();
        server.expect(requestTo(BASE + "/users/u1/role-mappings/realm")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.addRealmRole("u1", MANAGER)).isInstanceOf(KeycloakConfigurationException.class);
    }

    @Test
    void notFoundDuringChangeIsTranslated() {
        server.expect(requestTo(BASE + "/users/u1/role-mappings/realm")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.removeRealmRole("u1", MANAGER)).isInstanceOf(KeycloakNotFoundException.class);
    }

    @Test
    void serverErrorAndTimeoutMeanUnavailable() {
        server.expect(requestTo(BASE + "/roles?briefRepresentation=true")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> client.listRealmRoles()).isInstanceOf(KeycloakUnavailableException.class);

        server.reset();
        server.expect(requestTo(BASE + "/users/u1")).andRespond(withException(new SocketTimeoutException("timeout")));
        assertThatThrownBy(() -> client.findUser("u1")).isInstanceOf(KeycloakUnavailableException.class);
    }
}
