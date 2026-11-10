package pl.stojak.miniiam.user.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pl.stojak.miniiam.user.config.CorrelationIdFilter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityProbeController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class})
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor tokenWithRoles(String... roles) {
        SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[roles.length];
        for (int i = 0; i < roles.length; i++) {
            authorities[i] = new SimpleGrantedAuthority("ROLE_" + roles[i]);
        }
        return jwt().jwt(token -> token.subject("kc-user-1")).authorities(authorities);
    }

    @Test
    void anonymousRequestGets401AsProblemDetailWithCorrelationId() throws Exception {
        mockMvc.perform(get("/api/probe").header(CorrelationIdFilter.HEADER_NAME, "sec-test-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "sec-test-1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/probe"));
    }

    @Test
    void invalidBearerTokenGets401() throws Exception {
        mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."));
    }

    @Test
    void authenticatedUserCanCallApi() throws Exception {
        mockMvc.perform(get("/api/probe").with(tokenWithRoles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotCallAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/probe/admin").with(tokenWithRoles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminCanCallAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/probe/admin").with(tokenWithRoles("ADMIN", "USER")))
                .andExpect(status().isOk());
    }

    @Test
    void internalApiRequiresServiceRole() throws Exception {
        mockMvc.perform(get("/internal/api/probe")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/api/probe").with(tokenWithRoles("ADMIN", "USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/api/probe").with(tokenWithRoles("SERVICE")))
                .andExpect(status().isOk());
    }

    @Test
    void pathsWithoutExplicitRuleAreDenied() throws Exception {
        mockMvc.perform(get("/other/probe").with(tokenWithRoles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsPreflightAllowedOnlyForKnownOrigin() throws Exception {
        mockMvc.perform(options("/api/probe")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));

        mockMvc.perform(options("/api/probe")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
