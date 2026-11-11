package pl.stojak.miniiam.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.stojak.miniiam.user.integration.audit.AuditEventPublisher;
import pl.stojak.miniiam.user.integration.audit.AuditEventType;
import pl.stojak.miniiam.user.repository.UserProfileRepository;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end user management against real PostgreSQL and Keycloak (same realm import as docker compose).
 * The caller is simulated with a mock JWT; all Keycloak Admin API calls are real. audit-service is replaced by a
 * mock publisher (its Feign client is covered by AuditEventPublisherTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserServiceKeycloakIT {

    private static final String REALM = "mini-iam";
    private static final String CLIENT_SECRET = "it-client-secret";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("init-schema.sql");

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.2")
            .withRealmImportFile("keycloak/mini-iam-realm.json")
            .withEnv("MINI_IAM_ADMIN_CLIENT_SECRET", CLIENT_SECRET)
            .withEnv("MINI_IAM_ADMIN_PASSWORD", "it-admin-password");

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("app.keycloak.admin.base-url", KEYCLOAK::getAuthServerUrl);
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token");
        registry.add("spring.security.oauth2.client.registration.keycloak-admin.client-secret", () -> CLIENT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserProfileRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditEventPublisher auditEventPublisher;

    private final RestClient restClient = RestClient.create();

    private static RequestPostProcessor as(String subject, String... roles) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private static RequestPostProcessor admin() {
        return as("kc-it-admin", "ADMIN", "USER");
    }

    private JsonNode createUser(String username) throws Exception {
        String body = """
                {"username":"%s","email":"%s@example.com","firstName":"Test","lastName":"User",
                 "temporaryPassword":"Temp-Pass-123"}""".formatted(username, username);
        String response = mockMvc.perform(post("/api/users").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    @Test
    void createdUserExistsInKeycloakWithTemporaryPasswordAndBaseRole() throws Exception {
        JsonNode created = createUser("it.create");
        String keycloakId = created.get("keycloakId").asText();

        JsonNode account = keycloakAdminGet("/users/" + keycloakId);
        assertThat(account.get("username").asText()).isEqualTo("it.create");
        assertThat(account.get("enabled").asBoolean()).isTrue();
        assertThat(account.get("requiredActions").toString()).contains("UPDATE_PASSWORD");

        JsonNode roles = keycloakAdminGet("/users/" + keycloakId + "/role-mappings/realm/composite");
        assertThat(roles.findValuesAsText("name")).contains("USER").doesNotContain("ADMIN", "SERVICE");

        assertThat(repository.findByKeycloakId(keycloakId)).isPresent();
        mockMvc.perform(get("/api/users").param("search", "IT.CRE").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("it.create"));
    }

    @Test
    void duplicateUsernameIsRejectedWithConflict() throws Exception {
        createUser("it.duplicate");

        mockMvc.perform(post("/api/users").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"it.duplicate","email":"other@example.com","temporaryPassword":"Temp-Pass-123"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_OR_EMAIL_TAKEN"));
    }

    @Test
    void updateAndStatusChangesArePropagatedToKeycloak() throws Exception {
        JsonNode created = createUser("it.update");
        String id = created.get("id").asText();
        String keycloakId = created.get("keycloakId").asText();

        mockMvc.perform(put("/api/users/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"it.update.new@example.com","firstName":"Changed","lastName":null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("it.update.new@example.com"));
        JsonNode account = keycloakAdminGet("/users/" + keycloakId);
        assertThat(account.get("email").asText()).isEqualTo("it.update.new@example.com");
        assertThat(account.get("firstName").asText()).isEqualTo("Changed");
        assertThat(account.path("lastName").asText("")).isEmpty();
        assertThat(account.get("emailVerified").asBoolean()).as("new e-mail must not stay verified").isFalse();

        mockMvc.perform(patch("/api/users/" + id + "/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        JsonNode disabled = keycloakAdminGet("/users/" + keycloakId);
        assertThat(disabled.get("enabled").asBoolean()).isFalse();
        assertThat(disabled.get("email").asText()).as("status change keeps profile fields")
                .isEqualTo("it.update.new@example.com");

        mockMvc.perform(patch("/api/users/" + id + "/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        assertThat(keycloakAdminGet("/users/" + keycloakId).get("enabled").asBoolean()).isTrue();
    }

    @Test
    void lastActiveAdministratorIsProtectedAndGetsProfileJustInTime() throws Exception {
        String adminKeycloakId = keycloakAdminGet("/users?username=admin&exact=true").get(0).get("id").asText();

        String me = mockMvc.perform(get("/api/users/me").with(as(adminKeycloakId, "ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andReturn().getResponse().getContentAsString();
        String adminProfileId = objectMapper.readTree(me).get("id").asText();

        mockMvc.perform(patch("/api/users/" + adminProfileId + "/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        mockMvc.perform(delete("/api/users/" + adminProfileId).with(admin()))
                .andExpect(status().isConflict());
        assertThat(keycloakAdminGet("/users/" + adminKeycloakId).get("enabled").asBoolean()).isTrue();
    }

    @Test
    void managerCannotModifyAdministrator() throws Exception {
        String adminKeycloakId = keycloakAdminGet("/users?username=admin&exact=true").get(0).get("id").asText();
        String me = mockMvc.perform(get("/api/users/me").with(as(adminKeycloakId, "ADMIN", "USER")))
                .andReturn().getResponse().getContentAsString();
        String adminProfileId = objectMapper.readTree(me).get("id").asText();

        mockMvc.perform(put("/api/users/" + adminProfileId).with(as("kc-it-manager", "MANAGER", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"takeover@example.com","firstName":"X","lastName":"Y"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_PROTECTED"));
    }

    @Test
    void deleteRemovesKeycloakAccountAndProfile() throws Exception {
        JsonNode created = createUser("it.delete");
        String keycloakId = created.get("keycloakId").asText();

        mockMvc.perform(delete("/api/users/" + created.get("id").asText()).with(admin()))
                .andExpect(status().isNoContent());

        HttpStatusCode status = restClient.get()
                .uri(KEYCLOAK.getAuthServerUrl() + "/admin/realms/" + REALM + "/users/" + keycloakId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .exchange((request, response) -> response.getStatusCode());
        assertThat(status.value()).isEqualTo(404);
        assertThat(repository.findById(UUID.fromString(created.get("id").asText()))).isEmpty();

        verify(auditEventPublisher).publish(argThat(event -> event.eventType() == AuditEventType.USER_CREATED
                && keycloakId.equals(event.targetUserId()) && "kc-it-admin".equals(event.actorUserId())));
        verify(auditEventPublisher).publish(argThat(event -> event.eventType() == AuditEventType.USER_DELETED
                && keycloakId.equals(event.targetUserId()) && "it.delete".equals(event.details().get("username"))));
    }

    // ---------------------------------------------------------------- Keycloak helpers (master admin)

    private JsonNode keycloakAdminGet(String path) {
        return restClient.get()
                .uri(KEYCLOAK.getAuthServerUrl() + "/admin/realms/" + REALM + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .retrieve()
                .body(JsonNode.class);
    }

    private String masterAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", KEYCLOAK.getAdminUsername());
        form.add("password", KEYCLOAK.getAdminPassword());
        JsonNode token = restClient.post()
                .uri(KEYCLOAK.getAuthServerUrl() + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        return token.get("access_token").asText();
    }
}
