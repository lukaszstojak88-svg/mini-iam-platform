package pl.stojak.miniiam.auth;

import com.fasterxml.jackson.databind.JsonNode;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.stojak.miniiam.auth.integration.audit.AuditEventPublisher;
import pl.stojak.miniiam.auth.integration.audit.AuditEventRequest;
import pl.stojak.miniiam.auth.integration.audit.AuditEventType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role management end-to-end against a real Keycloak (same realm import as docker compose): every Admin API call
 * is real. Admin callers are simulated with mock JWTs (the realm admin has no password-grant client); regular
 * users log in for real through a test client, so "authorization follows the token" is verified with real tokens.
 * audit-service is replaced by a mock publisher (its Feign client is covered by AuditEventPublisherTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RoleManagementKeycloakIT {

    private static final String REALM = "mini-iam";
    private static final String CLIENT_SECRET = "it-client-secret";
    private static final String TEST_CLIENT = "it-password-client";
    private static final String PASSWORD = "It-Passw0rd!";

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.2")
            .withRealmImportFile("keycloak/mini-iam-realm.json")
            .withEnv("MINI_IAM_ADMIN_CLIENT_SECRET", CLIENT_SECRET)
            .withEnv("MINI_IAM_SERVICE_CLIENT_SECRET", "it-service-client-secret")
            .withEnv("MINI_IAM_ADMIN_PASSWORD", "it-admin-password");

    private static final RestClient REST = RestClient.create();
    private static String realmAdminId;

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", RoleManagementKeycloakIT::realmUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> realmUrl() + "/protocol/openid-connect/certs");
        registry.add("app.keycloak.admin.base-url", KEYCLOAK::getAuthServerUrl);
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> realmUrl() + "/protocol/openid-connect/token");
        registry.add("spring.security.oauth2.client.registration.keycloak-admin.client-secret", () -> CLIENT_SECRET);
        registry.add("spring.security.oauth2.client.registration.mini-iam-service.client-secret",
                () -> "it-service-client-secret");
    }

    @BeforeAll
    static void setUpRealm() {
        createPasswordGrantTestClient();
        realmAdminId = findUserId("admin");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditEventPublisher auditEventPublisher;

    // ---------------------------------------------------------------- scenarios

    @Test
    void listsApplicationRolesOfTheRealm() throws Exception {
        mockMvc.perform(get("/api/roles").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("ADMIN"))
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[2].name").value("USER"))
                .andExpect(jsonPath("$[2].assignable").value(false))
                .andExpect(jsonPath("$[?(@.name == 'SERVICE')]").isEmpty());
    }

    @Test
    void adminAssignsAndRemovesManagerAndTheUsersTokenFollows() throws Exception {
        String userId = createUser("it-manager-" + shortId());

        mockMvc.perform(get(rolesUrl(userId)).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("USER")));

        mockMvc.perform(assign(userId, "MANAGER").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("MANAGER", "USER")));
        assertThat(keycloakDirectRoles(userId)).contains("MANAGER");

        mockMvc.perform(assign(userId, "MANAGER").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ALREADY_ASSIGNED"));

        // A fresh token of the user carries the new role; MANAGER still cannot change roles (spec §12.3 p. 7).
        String managerToken = userToken(userId);
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("MANAGER", "USER")));
        mockMvc.perform(assign(realmAdminId, "AUDITOR").header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete(rolesUrl(userId) + "/MANAGER").with(admin()))
                .andExpect(status().isNoContent());
        assertThat(keycloakDirectRoles(userId)).doesNotContain("MANAGER");
        mockMvc.perform(delete(rolesUrl(userId) + "/MANAGER").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNED"));

        // Only the two successful changes are audited (not the duplicate or the 404).
        verify(auditEventPublisher, times(1)).publish(argThat(event ->
                isRoleEvent(event, AuditEventType.ROLE_ASSIGNED, userId)));
        verify(auditEventPublisher, times(1)).publish(argThat(event ->
                isRoleEvent(event, AuditEventType.ROLE_REMOVED, userId)));
    }

    private static boolean isRoleEvent(AuditEventRequest event, AuditEventType type, String userId) {
        return event.eventType() == type && userId.equals(event.targetUserId())
                && event.targetUsername() != null && event.targetUsername().startsWith("it-manager-")
                && realmAdminId.equals(event.actorUserId()) && Map.of("roleName", "MANAGER").equals(event.details());
    }

    @Test
    void regularUserReadsOnlyOwnRolesAndCannotAssignRoles() throws Exception {
        String userId = createUser("it-user-" + shortId());
        String token = userToken(userId);

        mockMvc.perform(get(rolesUrl(userId)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("USER")));
        mockMvc.perform(get(rolesUrl(realmAdminId)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(assign(userId, "ADMIN").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    void baseAndTechnicalRolesCannotBeChanged() throws Exception {
        String userId = createUser("it-base-" + shortId());

        for (String role : List.of("USER", "SERVICE", "default-roles-mini-iam")) {
            mockMvc.perform(assign(userId, role).with(admin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"));
        }
        mockMvc.perform(delete(rolesUrl(userId) + "/USER").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"));
        assertThat(keycloakEffectiveRoles(userId)).contains("USER").doesNotContain("SERVICE");
    }

    @Test
    void lastActiveAdministratorKeepsAdminRole() throws Exception {
        // The caller is another administrator whose ADMIN role is not a direct, active member (e.g. a mock JWT).
        mockMvc.perform(delete(rolesUrl(realmAdminId) + "/ADMIN").with(as("kc-other-admin", "ADMIN", "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        assertThat(keycloakDirectRoles(realmAdminId)).contains("ADMIN");

        // With a second active administrator the role can be removed - and given back.
        String secondAdmin = createUser("it-admin-" + shortId());
        mockMvc.perform(assign(secondAdmin, "ADMIN").with(admin())).andExpect(status().isOk());
        mockMvc.perform(delete(rolesUrl(secondAdmin) + "/ADMIN").with(admin())).andExpect(status().isNoContent());
    }

    @Test
    void administratorCannotChangeOwnRoles() throws Exception {
        mockMvc.perform(delete(rolesUrl(realmAdminId) + "/ADMIN").with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_ROLE_CHANGE"));
        mockMvc.perform(assign(realmAdminId, "AUDITOR").with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_ROLE_CHANGE"));
    }

    @Test
    void serviceAccountsAndUnknownUsersAreRejected() throws Exception {
        // Both technical accounts carry the SERVICE role mapping that marks service accounts. For mini-iam-admin
        // it is not in the client's scope mapping, so it never reaches that client's tokens.
        for (String serviceAccount : List.of("service-account-mini-iam-admin", "service-account-mini-iam-service")) {
            mockMvc.perform(assign(findUserId(serviceAccount), "ADMIN").with(admin()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SERVICE_ACCOUNT_PROTECTED"));
        }
        mockMvc.perform(get(rolesUrl(UUID.randomUUID().toString())).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- helpers: API under test

    private static String rolesUrl(String userId) {
        return "/api/users/" + userId + "/roles";
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder assign(
            String userId, String roleName) {
        return post(rolesUrl(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"" + roleName + "\"}");
    }

    private static RequestPostProcessor as(String subject, String... roles) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    /** The realm administrator from the import (direct, active ADMIN). */
    private static RequestPostProcessor admin() {
        return as(realmAdminId, "ADMIN", "USER");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------------------------------------------------------------- helpers: Keycloak (master admin)

    private static String realmUrl() {
        return KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM;
    }

    private static String adminApi() {
        return KEYCLOAK.getAuthServerUrl() + "/admin/realms/" + REALM;
    }

    private static String masterAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", KEYCLOAK.getAdminUsername());
        form.add("password", KEYCLOAK.getAdminPassword());
        return requestToken(KEYCLOAK.getAuthServerUrl() + "/realms/master", form);
    }

    private static String requestToken(String realmUrl, MultiValueMap<String, String> form) {
        JsonNode response = REST.post()
                .uri(realmUrl + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        assertThat(response).isNotNull();
        return response.get("access_token").asText();
    }

    /** Public test-only client with direct access grants and the API audience; full scope, so roles are in tokens. */
    private static void createPasswordGrantTestClient() {
        REST.post()
                .uri(adminApi() + "/clients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "clientId", TEST_CLIENT,
                        "publicClient", true,
                        "standardFlowEnabled", false,
                        "directAccessGrantsEnabled", true,
                        "fullScopeAllowed", true,
                        "protocolMappers", List.of(Map.of(
                                "name", "mini-iam-api-audience",
                                "protocol", "openid-connect",
                                "protocolMapper", "oidc-audience-mapper",
                                "config", Map.of(
                                        "included.custom.audience", "mini-iam-api",
                                        "access.token.claim", "true")))))
                .retrieve()
                .toBodilessEntity();
    }

    private static String createUser(String username) {
        REST.post()
                .uri(adminApi() + "/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", username,
                        "email", username + "@example.com",
                        "emailVerified", true,
                        "firstName", "It",
                        "lastName", "User",
                        "enabled", true,
                        "credentials", List.of(Map.of("type", "password", "value", PASSWORD, "temporary", false))))
                .retrieve()
                .toBodilessEntity();
        return findUserId(username);
    }

    private static String findUserId(String username) {
        List<Map<String, Object>> users = REST.get()
                .uri(adminApi() + "/users?username={u}&exact=true", username)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(users).hasSize(1);
        return String.valueOf(users.getFirst().get("id"));
    }

    private static String userToken(String userId) {
        Map<String, Object> user = REST.get()
                .uri(adminApi() + "/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(user).isNotNull();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", TEST_CLIENT);
        form.add("username", String.valueOf(user.get("username")));
        form.add("password", PASSWORD);
        return requestToken(realmUrl(), form);
    }

    private static List<String> keycloakDirectRoles(String userId) {
        return roleNames(adminApi() + "/users/" + userId + "/role-mappings/realm");
    }

    private static List<String> keycloakEffectiveRoles(String userId) {
        return roleNames(adminApi() + "/users/" + userId + "/role-mappings/realm/composite");
    }

    private static List<String> roleNames(String url) {
        List<Map<String, Object>> roles = REST.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(roles).isNotNull();
        return roles.stream().map(role -> String.valueOf(role.get("name"))).toList();
    }
}
