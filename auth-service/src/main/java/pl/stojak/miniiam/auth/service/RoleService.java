package pl.stojak.miniiam.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.stojak.miniiam.auth.domain.AppRole;
import pl.stojak.miniiam.auth.dto.response.RoleResponse;
import pl.stojak.miniiam.auth.dto.response.UserRolesResponse;
import pl.stojak.miniiam.auth.exception.DomainException;
import pl.stojak.miniiam.auth.exception.ErrorCode;
import pl.stojak.miniiam.auth.integration.audit.AuditEventPublisher;
import pl.stojak.miniiam.auth.integration.audit.AuditEventRequest;
import pl.stojak.miniiam.auth.integration.audit.AuditEventType;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakAdminClient;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakRole;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakUser;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reading and changing realm roles of users (spec §2, §6.2). Keycloak is the only source of truth; auth-service
 * keeps no copy or cache of roles.
 * <p>
 * Rules: only ADMIN, MANAGER and AUDITOR can be assigned/removed (USER is the base role, SERVICE is technical);
 * nobody changes their own roles; service accounts are protected; a role cannot be assigned twice; the last
 * active administrator keeps ADMIN. Roles are assigned directly (no groups). The checks are not atomic with the
 * change - two concurrent removals of ADMIN from the last two administrators are a documented MVP limitation.
 * <p>
 * Every successful change is recorded in audit-service ({@code ROLE_ASSIGNED} / {@code ROLE_REMOVED}) right after
 * Keycloak accepted it; a failed audit call is logged and does not fail the operation (best effort, spec §7).
 */
@Service
public class RoleService {

    static final String SERVICE_ROLE = "SERVICE";

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final KeycloakAdminClient keycloak;
    private final AuditEventPublisher auditEventPublisher;

    public RoleService(KeycloakAdminClient keycloak, AuditEventPublisher auditEventPublisher) {
        this.keycloak = keycloak;
        this.auditEventPublisher = auditEventPublisher;
    }

    /** Application roles defined in the realm, in the order of {@link AppRole}. */
    public List<RoleResponse> listRoles() {
        List<KeycloakRole> realmRoles = keycloak.listRealmRoles();
        return realmRoles.stream()
                .flatMap(role -> AppRole.fromName(role.name())
                        .map(appRole -> new RoleResponse(appRole, role.description(), appRole.isAssignable()))
                        .stream())
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .toList();
    }

    public UserRolesResponse getUserRoles(String userId) {
        String id = requireUser(userId).id();
        return new UserRolesResponse(id, applicationRoles(keycloak.getEffectiveRealmRoles(id)));
    }

    public UserRolesResponse assignRole(String userId, String roleName, String actorId, String actorUsername) {
        AppRole role = assignableRole(roleName);
        ensureNotSelf(userId, actorId);
        KeycloakUser user = requireUser(userId);
        // Rules below use Keycloak's canonical id, never the raw path value (case or format variants).
        String id = user.id();
        ensureNotSelf(id, actorId);
        List<KeycloakRole> effective = keycloak.getEffectiveRealmRoles(id);
        ensureNotServiceAccount(effective);
        if (containsRole(effective, role.name())) {
            throw DomainException.conflict(ErrorCode.ROLE_ALREADY_ASSIGNED, "The user already has this role.");
        }
        KeycloakRole realmRole = keycloak.findRealmRole(role.name())
                .orElseThrow(() -> DomainException.notFound(ErrorCode.ROLE_NOT_FOUND, "Role does not exist in the realm."));

        keycloak.addRealmRole(id, realmRole);
        log.info("Role {} assigned to {} by {}", role, id, actorId);
        audit(AuditEventType.ROLE_ASSIGNED, actorId, actorUsername, user, role);
        return new UserRolesResponse(id, applicationRoles(keycloak.getEffectiveRealmRoles(id)));
    }

    public void removeRole(String userId, String roleName, String actorId, String actorUsername) {
        AppRole role = assignableRole(roleName);
        ensureNotSelf(userId, actorId);
        KeycloakUser user = requireUser(userId);
        String id = user.id();
        ensureNotSelf(id, actorId);
        ensureNotServiceAccount(keycloak.getEffectiveRealmRoles(id));
        KeycloakRole mapping = keycloak.getDirectRealmRoles(id).stream()
                .filter(r -> role.name().equals(r.name()))
                .findFirst()
                .orElseThrow(() -> DomainException.notFound(ErrorCode.ROLE_NOT_ASSIGNED,
                        "The user does not have this role assigned."));
        if (role == AppRole.ADMIN) {
            ensureNotLastActiveAdmin(id);
        }

        keycloak.removeRealmRole(id, mapping);
        log.info("Role {} removed from {} by {}", role, id, actorId);
        audit(AuditEventType.ROLE_REMOVED, actorId, actorUsername, user, role);
    }

    // ---------------------------------------------------------------- rules and helpers

    private void audit(AuditEventType type, String actorId, String actorUsername, KeycloakUser target,
                       AppRole role) {
        auditEventPublisher.publish(new AuditEventRequest(type, actorId, actorUsername, target.id(),
                target.username(), Map.of("roleName", role.name()), Instant.now()));
    }

    private static AppRole assignableRole(String roleName) {
        return AppRole.fromName(roleName)
                .filter(AppRole::isAssignable)
                .orElseThrow(() -> DomainException.badRequest(ErrorCode.ROLE_NOT_ASSIGNABLE,
                        "Only the roles ADMIN, MANAGER and AUDITOR can be assigned or removed."));
    }

    private static void ensureNotSelf(String userId, String actorId) {
        if (userId.equals(actorId)) {
            throw DomainException.forbidden(ErrorCode.SELF_ROLE_CHANGE, "Users cannot change their own roles.");
        }
    }

    private KeycloakUser requireUser(String userId) {
        return keycloak.findUser(userId)
                .orElseThrow(() -> DomainException.notFound(ErrorCode.USER_NOT_FOUND, "User not found."));
    }

    /**
     * Hardening: roles of technical accounts are managed only in the realm configuration. Technical accounts are
     * recognised by the assigned SERVICE realm role - every service account in the realm import must keep it
     * (also {@code service-account-mini-iam-admin}, where it is not in the token), since the Admin API user
     * representation does not identify service accounts.
     */
    private static void ensureNotServiceAccount(Collection<KeycloakRole> effectiveRoles) {
        if (containsRole(effectiveRoles, SERVICE_ROLE)) {
            throw DomainException.forbidden(ErrorCode.SERVICE_ACCOUNT_PROTECTED,
                    "Roles of service accounts cannot be changed.");
        }
    }

    /**
     * Spec §2, §6.2: the last active administrator must keep ADMIN. Counts direct members of ADMIN (roles are
     * assigned directly, not through groups) that are enabled.
     */
    private void ensureNotLastActiveAdmin(String userId) {
        List<String> activeAdmins = keycloak.getRealmRoleMembers(AppRole.ADMIN.name()).stream()
                .filter(KeycloakUser::enabled)
                .map(KeycloakUser::id)
                .toList();
        if (activeAdmins.contains(userId) && activeAdmins.size() == 1) {
            log.warn("Refused to remove ADMIN from the last active administrator {}", userId);
            throw DomainException.conflict(ErrorCode.LAST_ADMIN,
                    "The ADMIN role cannot be removed from the last active administrator.");
        }
    }

    private static boolean containsRole(Collection<KeycloakRole> roles, String name) {
        return roles.stream().anyMatch(role -> name.equals(role.name()));
    }

    private static List<AppRole> applicationRoles(Collection<KeycloakRole> roles) {
        return roles.stream()
                .map(KeycloakRole::name)
                .map(AppRole::fromName)
                .flatMap(Optional::stream)
                .distinct()
                .sorted()
                .toList();
    }
}
