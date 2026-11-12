package pl.stojak.miniiam.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.stojak.miniiam.auth.config.CorrelationIdFilter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec §11.1 / §16.3: tokens and the Authorization header never reach the logs, and every request-completion log
 * line carries the correlation ID. Assertions do not depend on the log format (text locally, ECS JSON in Docker);
 * the ECS output in Docker is checked by scripts/e2e-smoke.sh.
 */
@WebMvcTest(controllers = SecurityProbeController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class})
@ExtendWith(OutputCaptureExtension.class)
class LogHygieneTest {

    // Token-like values that must never show up in the output (also not partially).
    private static final String VALID_TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJsb2ctdGVzdCJ9.VALID-signature-value";
    private static final String REJECTED_TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJyZWplY3RlZCJ9.REJECTED-signature";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void stubDecoder() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            if (!VALID_TOKEN.equals(invocation.getArgument(0))) {
                throw new BadJwtException("Invalid signature");
            }
            return Jwt.withTokenValue(VALID_TOKEN)
                    .header("alg", "RS256")
                    .subject("kc-log-test")
                    .claim("realm_access", Map.of("roles", List.of("USER")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        });
    }

    @Test
    void acceptedTokenIsNotLoggedAndLogCarriesCorrelationId(CapturedOutput output) throws Exception {
        // USER on an ADMIN endpoint: passes authentication and produces an access-denied log entry.
        mockMvc.perform(get("/api/probe/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .header(CorrelationIdFilter.HEADER_NAME, "log-test-1"))
                .andExpect(status().isForbidden());

        assertThat(output.getOut()).contains("Access denied");
        assertLoggedWithCorrelationId(output, "GET /api/probe/admin -> 403", "log-test-1");
        assertNoCredentials(output, VALID_TOKEN);
    }

    @Test
    void rejectedTokenIsNotLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + REJECTED_TOKEN)
                        .header(CorrelationIdFilter.HEADER_NAME, "log-test-2"))
                .andExpect(status().isUnauthorized());

        assertLoggedWithCorrelationId(output, "Unauthenticated request rejected", "log-test-2");
        assertLoggedWithCorrelationId(output, "GET /api/probe -> 401", "log-test-2");
        assertNoCredentials(output, REJECTED_TOKEN);
    }

    @Test
    void generatedCorrelationIdIsReturnedAndLogged(CapturedOutput output) throws Exception {
        String correlationId = mockMvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);

        assertThat(correlationId).matches("[0-9a-f-]{36}");
        assertLoggedWithCorrelationId(output, "GET /api/probe -> 401", correlationId);
    }

    private static void assertLoggedWithCorrelationId(CapturedOutput output, String message, String correlationId) {
        assertThat(output.getOut().lines().filter(line -> line.contains(message)))
                .as("log line '%s' carries correlation ID %s", message, correlationId)
                .isNotEmpty()
                .allMatch(line -> line.contains(correlationId));
    }

    private static void assertNoCredentials(CapturedOutput output, String token) {
        String[] tokenParts = token.split("\\.");
        assertThat(output.getAll())
                .doesNotContain(token)
                .doesNotContain(tokenParts[1])
                .doesNotContain(tokenParts[2])
                .doesNotContainIgnoringCase("Bearer ")
                .doesNotContainIgnoringCase("Authorization");
    }
}
