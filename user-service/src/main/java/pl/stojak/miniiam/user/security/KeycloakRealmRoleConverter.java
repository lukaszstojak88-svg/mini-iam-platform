package pl.stojak.miniiam.user.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maps Keycloak role claims to Spring Security authorities (spec §5.3).
 * <p>
 * Realm roles from {@code realm_access.roles} become {@code ROLE_<NAME>}. Client roles from
 * {@code resource_access.<clientId>.roles} are included only when a client id is configured.
 * Keycloak built-in roles (default-roles-*, offline_access, uma_authorization) and names that are not
 * upper-case constants are ignored, so only domain roles such as ADMIN or SERVICE reach authorization rules.
 * The authentication name is the token subject ({@code sub} = Keycloak user id).
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String REALM_ACCESS_CLAIM = "realm_access";
    static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    static final String ROLES_KEY = "roles";
    static final String ROLE_PREFIX = "ROLE_";

    private static final Pattern DOMAIN_ROLE_NAME = Pattern.compile("^[A-Z][A-Z_]*$");

    private final String clientId;

    public KeycloakRealmRoleConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        addRoles(authorities, jwt.getClaim(REALM_ACCESS_CLAIM));
        if (StringUtils.hasText(clientId)) {
            Object resourceAccess = jwt.getClaim(RESOURCE_ACCESS_CLAIM);
            if (resourceAccess instanceof Map<?, ?> clients) {
                addRoles(authorities, clients.get(clientId));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private static void addRoles(Set<GrantedAuthority> authorities, Object accessClaim) {
        if (!(accessClaim instanceof Map<?, ?> access)) {
            return;
        }
        if (!(access.get(ROLES_KEY) instanceof Collection<?> roles)) {
            return;
        }
        for (Object role : roles) {
            if (role instanceof String name && DOMAIN_ROLE_NAME.matcher(name).matches()) {
                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + name));
            }
        }
    }
}
