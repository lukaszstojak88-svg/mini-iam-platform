package pl.stojak.miniiam.auth.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import pl.stojak.miniiam.auth.domain.AppRole;
import pl.stojak.miniiam.auth.dto.response.CurrentUserResponse;

import java.util.List;
import java.util.Optional;

/**
 * Builds the current user's access summary from the JWT only (no Keycloak calls). Roles reflect the token,
 * so a role change becomes visible after the token is refreshed (spec §11.2).
 */
@Service
public class CurrentUserService {

    public CurrentUserResponse describe(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        List<AppRole> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(AppRole::fromAuthority)
                .flatMap(Optional::stream)
                .distinct()
                .sorted()
                .toList();
        return new CurrentUserResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                roles);
    }
}
