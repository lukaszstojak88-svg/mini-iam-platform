package pl.stojak.miniiam.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import pl.stojak.miniiam.auth.config.CorrelationIdFilter;
import pl.stojak.miniiam.auth.security.KeycloakRealmRoleConverter;
import pl.stojak.miniiam.auth.security.SecurityConfig;
import pl.stojak.miniiam.auth.service.CurrentUserService;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, CurrentUserService.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsIdentityAndOnlyApplicationRolesFromToken() throws Exception {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("kc-123")
                .claim("preferred_username", "john.doe")
                .claim("email", "john.doe@example.com")
                .claim("given_name", "John")
                .claim("family_name", "Doe")
                .claim("realm_access", Map.of("roles",
                        List.of("USER", "MANAGER", "SERVICE", "offline_access", "default-roles-mini-iam")))
                .build();
        // Authorities come from the real converter, so the Keycloak claim mapping is part of this test.
        var authorities = new KeycloakRealmRoleConverter("").convert(token).getAuthorities();

        mockMvc.perform(get("/api/auth/me").with(jwt().jwt(token).authorities(authorities)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("kc-123"))
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"))
                .andExpect(jsonPath("$.roles[1]").value("USER"));
    }

    @Test
    void anonymousCallerGets401() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
