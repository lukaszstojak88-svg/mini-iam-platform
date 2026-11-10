package pl.stojak.miniiam.audit.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("kc-user-1");
    }

    private static List<String> authorities(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = token().claim("realm_access", Map.of("roles", List.of("ADMIN", "USER"))).build();

        AbstractAuthenticationToken authentication = new KeycloakRealmRoleConverter("").convert(jwt);

        assertThat(authorities(authentication)).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        assertThat(authentication.getName()).isEqualTo("kc-user-1");
    }

    @Test
    void ignoresKeycloakBuiltInRoles() {
        Jwt jwt = token().claim("realm_access", Map.of("roles",
                List.of("default-roles-mini-iam", "offline_access", "uma_authorization", "MANAGER"))).build();

        assertThat(authorities(new KeycloakRealmRoleConverter("").convert(jwt))).containsExactly("ROLE_MANAGER");
    }

    @Test
    void returnsNoAuthoritiesWhenClaimsMissingOrMalformed() {
        assertThat(authorities(new KeycloakRealmRoleConverter("").convert(token().claim("x", "y").build()))).isEmpty();
        assertThat(authorities(new KeycloakRealmRoleConverter("")
                .convert(token().claim("realm_access", "not-a-map").build()))).isEmpty();
        assertThat(authorities(new KeycloakRealmRoleConverter("")
                .convert(token().claim("realm_access", Map.of("roles", "ADMIN")).build()))).isEmpty();
    }

    @Test
    void includesClientRolesOnlyForConfiguredClient() {
        Jwt jwt = token()
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .claim("resource_access", Map.of(
                        "mini-iam-api", Map.of("roles", List.of("AUDITOR")),
                        "realm-management", Map.of("roles", List.of("MANAGE_USERS"))))
                .build();

        assertThat(authorities(new KeycloakRealmRoleConverter("").convert(jwt))).containsExactly("ROLE_USER");
        assertThat(authorities(new KeycloakRealmRoleConverter("mini-iam-api").convert(jwt)))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_AUDITOR");
    }
}
