package pl.stojak.miniiam.auth;

import com.fasterxml.jackson.databind.JsonNode;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validates real Keycloak tokens end-to-end (spec §5.3, §11.1): signature, issuer and audience.
 * Uses the same realm import as docker compose.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class KeycloakJwtValidationIT {

    private static final String REALM = "mini-iam";
    private static final String ADMIN_CLIENT_SECRET = "it-client-secret";

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.2")
            .withRealmImportFile("keycloak/mini-iam-realm.json")
            .withEnv("MINI_IAM_ADMIN_CLIENT_SECRET", ADMIN_CLIENT_SECRET)
            .withEnv("MINI_IAM_ADMIN_PASSWORD", "it-admin-password");

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakJwtValidationIT::realmUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> realmUrl() + "/protocol/openid-connect/certs");
    }

    @Autowired
    private MockMvc mockMvc;

    private final RestClient restClient = RestClient.create();

    private static String realmUrl() {
        return KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM;
    }

    @Test
    void acceptsValidServiceAccountToken() throws Exception {
        String token = clientCredentialsToken(REALM, "mini-iam-admin", ADMIN_CLIENT_SECRET);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("service-account-mini-iam-admin"))
                // SERVICE is a technical role and never exposed as an application role
                .andExpect(jsonPath("$.roles.length()").value(0));
    }

    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception {
        String masterToken = masterAdminToken();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + masterToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutApiAudience() throws Exception {
        createConfidentialClientWithoutAudience("no-audience-client", "no-audience-secret");
        String token = clientCredentialsToken(REALM, "no-audience-client", "no-audience-secret");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithTamperedSignature() throws Exception {
        String token = clientCredentialsToken(REALM, "mini-iam-admin", ADMIN_CLIENT_SECRET);
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    private String clientCredentialsToken(String realm, String clientId, String secret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        return requestToken(realm, form);
    }

    private String masterAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", KEYCLOAK.getAdminUsername());
        form.add("password", KEYCLOAK.getAdminPassword());
        return requestToken("master", form);
    }

    private String requestToken(String realm, MultiValueMap<String, String> form) {
        JsonNode response = restClient.post()
                .uri(KEYCLOAK.getAuthServerUrl() + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        assertThat(response).isNotNull();
        return response.get("access_token").asText();
    }

    private void createConfidentialClientWithoutAudience(String clientId, String secret) {
        restClient.post()
                .uri(KEYCLOAK.getAuthServerUrl() + "/admin/realms/" + REALM + "/clients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "clientId", clientId,
                        "secret", secret,
                        "publicClient", false,
                        "standardFlowEnabled", false,
                        "serviceAccountsEnabled", true))
                .retrieve()
                .toBodilessEntity();
    }
}
