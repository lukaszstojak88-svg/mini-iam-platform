package pl.stojak.miniiam.auth.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.stojak.miniiam.auth.dto.request.AssignRoleRequest;
import pl.stojak.miniiam.auth.dto.response.UserRolesResponse;
import pl.stojak.miniiam.auth.service.RoleService;

/**
 * Realm roles of a user (spec §6.2). {@code {userId}} is the Keycloak user id (keycloakId), as returned by
 * user-service; the actor is the token subject.
 */
@RestController
@RequestMapping("/api/users/{userId}/roles")
public class UserRoleController {

    /** Keycloak ids are UUIDs; the pattern also rejects anything that could alter the Admin API path. */
    static final String USER_ID_PATTERN = "^[A-Za-z0-9-]{1,64}$";

    private final RoleService roleService;

    public UserRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** ADMIN reads anyone's roles; every other user only their own (spec §2.1). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.name")
    public UserRolesResponse get(@PathVariable @Pattern(regexp = USER_ID_PATTERN) String userId) {
        return roleService.getUserRoles(userId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserRolesResponse assign(@PathVariable @Pattern(regexp = USER_ID_PATTERN) String userId,
                                    @Valid @RequestBody AssignRoleRequest request,
                                    JwtAuthenticationToken authentication) {
        return roleService.assignRole(userId, request.roleName(), authentication.getName(),
                actorUsername(authentication));
    }

    @DeleteMapping("/{roleName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable @Pattern(regexp = USER_ID_PATTERN) String userId,
                                       @PathVariable String roleName,
                                       JwtAuthenticationToken authentication) {
        roleService.removeRole(userId, roleName, authentication.getName(), actorUsername(authentication));
        return ResponseEntity.noContent().build();
    }

    /** Username of the caller, stored in the audit event next to the keycloakId. */
    private static String actorUsername(JwtAuthenticationToken authentication) {
        return authentication.getToken().getClaimAsString("preferred_username");
    }
}
