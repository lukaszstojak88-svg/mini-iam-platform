package pl.stojak.miniiam.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pl.stojak.miniiam.user.domain.UserProfile;
import pl.stojak.miniiam.user.dto.request.CreateUserRequest;
import pl.stojak.miniiam.user.dto.request.UpdateProfileRequest;
import pl.stojak.miniiam.user.dto.response.PageResponse;
import pl.stojak.miniiam.user.dto.response.UserDetailsResponse;
import pl.stojak.miniiam.user.dto.response.UserResponse;
import pl.stojak.miniiam.user.exception.BusinessRuleViolationException;
import pl.stojak.miniiam.user.exception.ErrorCode;
import pl.stojak.miniiam.user.exception.InvalidRequestException;
import pl.stojak.miniiam.user.exception.OperationForbiddenException;
import pl.stojak.miniiam.user.exception.UserNotFoundException;
import pl.stojak.miniiam.user.exception.UserSyncException;
import pl.stojak.miniiam.user.integration.audit.AuditEventPublisher;
import pl.stojak.miniiam.user.integration.audit.AuditEventRequest;
import pl.stojak.miniiam.user.integration.audit.AuditEventType;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakAdminClient;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakIntegrationException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakNotFoundException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakUnavailableException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakUser;
import pl.stojak.miniiam.user.integration.keycloak.NewKeycloakUser;
import pl.stojak.miniiam.user.mapper.UserMapper;
import pl.stojak.miniiam.user.repository.UserProfileRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * User account management (spec §6.1). Keycloak owns the account (credentials, roles, {@code enabled});
 * the local profile mirrors it for the application.
 * <p>
 * Every operation calls Keycloak first and then writes locally, each step outside a long DB transaction
 * (repository calls are individually transactional). When the local write fails, the Keycloak change is
 * compensated (best effort); if compensation fails too, the inconsistency is logged with the identifiers needed
 * for manual reconciliation and a generic error is returned. There is no distributed transaction.
 * <p>
 * Every successful change is recorded in audit-service after it was applied in Keycloak and in the database.
 * Recording is best effort (spec §7): a failure is logged and does not fail the operation. Events carry no
 * secrets and as little personal data as possible: the actor and target usernames and, in details, only the
 * username (create/delete, kept for consistency with older events) or the names (not values) of changed fields.
 */
@Service
public class UserService {

    static final String ADMIN_ROLE = "ADMIN";
    static final String MANAGER_ROLE = "MANAGER";
    static final String SERVICE_ROLE = "SERVICE";
    /** Upper limit of {@code size}; larger values are rejected with 400, never silently clamped. */
    static final int MAX_PAGE_SIZE = 100;
    static final Set<String> SORTABLE_PROPERTIES =
            Set.of("username", "email", "firstName", "lastName", "enabled", "createdAt", "updatedAt");

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserProfileRepository repository;
    private final KeycloakAdminClient keycloak;
    private final AuditEventPublisher auditEventPublisher;

    public UserService(UserProfileRepository repository, KeycloakAdminClient keycloak,
                       AuditEventPublisher auditEventPublisher) {
        this.repository = repository;
        this.keycloak = keycloak;
        this.auditEventPublisher = auditEventPublisher;
    }

    // ---------------------------------------------------------------- queries

    public PageResponse<UserResponse> list(String search, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("'size' must not exceed " + MAX_PAGE_SIZE + ".");
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Unsupported sort property: " + order.getProperty());
            }
        }
        // Unique tie-breaker: without it, sorting by columns with many equal values (enabled, names)
        // gives non-deterministic paging (rows repeated or skipped between pages).
        Pageable stable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by("id")));
        Page<UserProfile> page = (search == null || search.isBlank())
                ? repository.findAll(stable)
                : repository.search(likePattern(search), stable);
        return PageResponse.of(page, UserMapper::toResponse);
    }

    /**
     * Returns a profile and reconciles its status with Keycloak, the source of truth for {@code enabled}
     * (spec §6.1). If Keycloak cannot be asked, the local data is returned and the problem is logged.
     */
    public UserDetailsResponse get(UUID id, Actor actor) {
        UserProfile profile = findProfile(id);
        try {
            Optional<KeycloakUser> account = keycloak.findUser(profile.getKeycloakId());
            if (account.isEmpty()) {
                log.error("Profile {} has no Keycloak account (keycloakId={}); manual reconciliation required",
                        profile.getId(), profile.getKeycloakId());
            } else if (account.get().enabled() != profile.isEnabled()) {
                log.warn("Status of profile {} differed from Keycloak (local={}, keycloak={}); local status updated",
                        profile.getId(), profile.isEnabled(), account.get().enabled());
                // Targeted update of the status only: never overwrite other columns with possibly stale data.
                repository.updateEnabled(profile.getId(), account.get().enabled(), Instant.now());
                profile.setEnabled(account.get().enabled());
            }
        } catch (KeycloakIntegrationException e) {
            log.warn("Could not verify status of profile {} in Keycloak; returning local data", profile.getId());
        }
        return new UserDetailsResponse(UserMapper.toResponse(profile), mayEdit(actor, profile.getKeycloakId()));
    }

    /**
     * Whether {@code update} would accept the actor's change of this profile. When Keycloak cannot be asked, a
     * manager gets {@code false} (fail closed; saving needs Keycloak anyway).
     */
    private boolean mayEdit(Actor actor, String keycloakId) {
        try {
            return !managerTargetsAdmin(actor, keycloakId);
        } catch (KeycloakIntegrationException e) {
            log.warn("Could not check the roles of {} in Keycloak; profile reported as not editable", keycloakId);
            return false;
        }
    }

    /**
     * Profile of the caller. Accounts created outside this service (realm import, Keycloak console) get their
     * profile created just in time from Keycloak data. Service accounts have no profile.
     */
    public UserResponse getOwnProfile(Actor actor) {
        return UserMapper.toResponse(ownProfile(actor));
    }

    // ---------------------------------------------------------------- commands

    public UserResponse create(CreateUserRequest request, Actor actor) {
        String username = request.username().toLowerCase(Locale.ROOT);
        String email = request.email().toLowerCase(Locale.ROOT);

        String keycloakId;
        try {
            keycloakId = keycloak.createUser(new NewKeycloakUser(
                    username, email, request.firstName(), request.lastName(), request.temporaryPassword()));
        } catch (KeycloakUnavailableException e) {
            // Timeout / 5xx: Keycloak may have created the account anyway - do not leave it without a profile.
            compensateAmbiguousCreate(username);
            throw e;
        }

        UserProfile profile;
        try {
            profile = repository.save(new UserProfile(
                    keycloakId, username, email, request.firstName(), request.lastName(), true));
        } catch (RuntimeException e) {
            compensateCreate(keycloakId, e);
            throw new UserSyncException("Saving the profile failed; the Keycloak account was rolled back", e);
        }
        log.info("User {} created (keycloakId={}) by {}", profile.getId(), keycloakId, actor.keycloakId());
        audit(AuditEventType.USER_CREATED, actor, profile, Map.of("username", username));
        return UserMapper.toResponse(profile);
    }

    public UserResponse update(UUID id, UpdateProfileRequest request, Actor actor) {
        UserProfile profile = findProfile(id);
        if (managerTargetsAdmin(actor, profile.getKeycloakId())) {
            // Hardening: a manager changing an admin's e-mail would be a path to taking over that account.
            throw new OperationForbiddenException(ErrorCode.ADMIN_ACCOUNT_PROTECTED,
                    "Managers cannot modify administrator accounts");
        }
        List<String> changedFields = changedFields(profile, request);
        UserProfile updated = applyProfileUpdate(profile, request);
        log.info("User {} updated by {}", updated.getId(), actor.keycloakId());
        auditUpdate(actor, updated, changedFields);
        return UserMapper.toResponse(updated);
    }

    public UserResponse updateOwnProfile(UpdateProfileRequest request, Actor actor) {
        UserProfile profile = ownProfile(actor);
        List<String> changedFields = changedFields(profile, request);
        UserProfile updated = applyProfileUpdate(profile, request);
        log.info("User {} updated own profile", updated.getId());
        auditUpdate(actor, updated, changedFields);
        return UserMapper.toResponse(updated);
    }

    public UserResponse updateStatus(UUID id, boolean enabled, Actor actor) {
        UserProfile profile = findProfile(id);
        if (!enabled) {
            ensureNotOwnAccount(profile, actor, "deactivate");
            ensureNotLastActiveAdmin(profile.getKeycloakId(), "deactivate");
        }
        // Revert target is the state in Keycloak (source of truth), not the possibly stale local flag.
        boolean previous = keycloak.findUser(profile.getKeycloakId())
                .map(KeycloakUser::enabled)
                .orElseThrow(() -> new KeycloakNotFoundException("updateStatus", null));
        keycloak.setEnabled(profile.getKeycloakId(), enabled);
        profile.setEnabled(enabled);
        UserProfile saved;
        try {
            saved = repository.save(profile);
        } catch (RuntimeException e) {
            revert("status", profile, () -> keycloak.setEnabled(profile.getKeycloakId(), previous), e);
            throw new UserSyncException("Saving the status failed", e);
        }
        log.info("User {} {} by {}", saved.getId(), enabled ? "activated" : "deactivated", actor.keycloakId());
        if (previous != enabled) {
            // Setting the status it already had is not an activation/deactivation.
            audit(enabled ? AuditEventType.USER_ACTIVATED : AuditEventType.USER_DEACTIVATED,
                    actor, saved, Map.of());
        }
        return UserMapper.toResponse(saved);
    }

    public void delete(UUID id, Actor actor) {
        UserProfile profile = findProfile(id);
        ensureNotOwnAccount(profile, actor, "delete");
        ensureNotLastActiveAdmin(profile.getKeycloakId(), "delete");
        if (!keycloak.deleteUser(profile.getKeycloakId())) {
            log.warn("Keycloak account {} of profile {} did not exist; deleting the local profile only",
                    profile.getKeycloakId(), profile.getId());
        }
        try {
            repository.delete(profile);
        } catch (RuntimeException e) {
            // The account is already gone from Keycloak (it can no longer log in); retrying DELETE is safe.
            log.error("Keycloak account {} deleted but local profile {} could not be removed; retry the deletion",
                    profile.getKeycloakId(), profile.getId(), e);
            throw new UserSyncException("Deleting the profile failed", e);
        }
        log.info("User {} (keycloakId={}) deleted by {}", profile.getId(), profile.getKeycloakId(), actor.keycloakId());
        // The username keeps the entry readable after the account is gone.
        audit(AuditEventType.USER_DELETED, actor, profile, Map.of("username", profile.getUsername()));
    }

    // ---------------------------------------------------------------- rules and helpers

    private void audit(AuditEventType type, Actor actor, UserProfile target, Map<String, Object> details) {
        auditEventPublisher.publish(new AuditEventRequest(type, actor.keycloakId(), actor.username(),
                target.getKeycloakId(), target.getUsername(), details, Instant.now()));
    }

    /** A save without any change is not audited. */
    private void auditUpdate(Actor actor, UserProfile updated, List<String> changedFields) {
        if (!changedFields.isEmpty()) {
            audit(AuditEventType.USER_UPDATED, actor, updated, Map.of("changedFields", changedFields));
        }
    }

    /** Names of the profile fields the request changes (values are not audited: personal data). */
    static List<String> changedFields(UserProfile profile, UpdateProfileRequest request) {
        List<String> changed = new ArrayList<>();
        if (!request.email().toLowerCase(Locale.ROOT).equals(profile.getEmail())) {
            changed.add("email");
        }
        if (!Objects.equals(request.firstName(), profile.getFirstName())) {
            changed.add("firstName");
        }
        if (!Objects.equals(request.lastName(), profile.getLastName())) {
            changed.add("lastName");
        }
        return List.copyOf(changed);
    }

    private UserProfile applyProfileUpdate(UserProfile profile, UpdateProfileRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        String previousEmail = profile.getEmail();
        String previousFirstName = profile.getFirstName();
        String previousLastName = profile.getLastName();

        boolean emailChanged = !email.equals(previousEmail);
        keycloak.updateProfile(profile.getKeycloakId(), email, request.firstName(), request.lastName(), emailChanged);
        profile.updateDetails(email, request.firstName(), request.lastName());
        try {
            return repository.save(profile);
        } catch (RuntimeException e) {
            // emailVerified stays false after a revert: conservative, the address must be verified again.
            revert("profile", profile, () -> keycloak.updateProfile(
                    profile.getKeycloakId(), previousEmail, previousFirstName, previousLastName, false), e);
            throw new UserSyncException("Saving the profile failed", e);
        }
    }

    /**
     * Nobody deactivates or deletes their own account (spec §2): it would end the caller's own session and could
     * lock the system out; another administrator has to do it.
     */
    private static void ensureNotOwnAccount(UserProfile profile, Actor actor, String operation) {
        if (profile.getKeycloakId().equals(actor.keycloakId())) {
            log.warn("Refused to {} own account {}", operation, actor.keycloakId());
            throw new OperationForbiddenException(ErrorCode.SELF_ACCOUNT_PROTECTED,
                    "You cannot " + operation + " your own account");
        }
    }

    /**
     * Spec §2: the last active ADMIN must not be deactivated or deleted. Assumes ADMIN is assigned directly
     * (as auth-service does), not through groups or composite roles. The check is not atomic with the change:
     * two concurrent deactivations of the last two admins are a documented MVP limitation.
     */
    private void ensureNotLastActiveAdmin(String keycloakId, String operation) {
        var activeAdmins = keycloak.getRealmRoleMembers(ADMIN_ROLE).stream()
                .filter(KeycloakUser::enabled)
                .map(KeycloakUser::id)
                .toList();
        if (activeAdmins.contains(keycloakId) && activeAdmins.size() == 1) {
            log.warn("Refused to {} the last active administrator {}", operation, keycloakId);
            throw new BusinessRuleViolationException(ErrorCode.LAST_ADMIN,
                    "The last active administrator cannot be deactivated or deleted");
        }
    }

    /** A manager (without ADMIN) may not modify administrator accounts. */
    private boolean managerTargetsAdmin(Actor actor, String keycloakId) {
        return actor.hasRole(MANAGER_ROLE) && !actor.hasRole(ADMIN_ROLE) && isAdmin(keycloakId);
    }

    private boolean isAdmin(String keycloakId) {
        return keycloak.getEffectiveRealmRoles(keycloakId).contains(ADMIN_ROLE);
    }

    private UserProfile ownProfile(Actor actor) {
        if (actor.hasRole(SERVICE_ROLE)) {
            throw new UserNotFoundException("Service accounts have no user profile");
        }
        return repository.findByKeycloakId(actor.keycloakId()).orElseGet(() -> provisionProfile(actor.keycloakId()));
    }

    private UserProfile provisionProfile(String keycloakId) {
        KeycloakUser account = keycloak.findUser(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("Account not found in Keycloak"));
        UserProfile profile = new UserProfile(keycloakId, account.username(),
                account.email() == null ? "" : account.email(),
                account.firstName(), account.lastName(), account.enabled());
        try {
            UserProfile saved = repository.save(profile);
            log.info("Profile {} provisioned just in time for Keycloak account {}", saved.getId(), keycloakId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created it first (unique keycloak_id).
            return repository.findByKeycloakId(keycloakId).orElseThrow(() -> e);
        }
    }

    private UserProfile findProfile(UUID id) {
        return repository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private void compensateAmbiguousCreate(String username) {
        try {
            Optional<String> orphan = keycloak.findUserIdByUsername(username);
            if (orphan.isPresent()) {
                keycloak.deleteUser(orphan.get());
                log.warn("Keycloak create of '{}' returned no result but the account {} exists; removed (compensation)",
                        username, orphan.get());
            }
        } catch (RuntimeException e) {
            log.error("Keycloak create of '{}' had an unknown outcome and the account could not be checked; "
                    + "verify manually that no account without a profile exists", username, e);
        }
    }

    private void compensateCreate(String keycloakId, RuntimeException cause) {
        try {
            keycloak.deleteUser(keycloakId);
            log.warn("Profile save failed; Keycloak account {} removed (compensation)", keycloakId, cause);
        } catch (RuntimeException compensationError) {
            log.error("Profile save failed and Keycloak account {} could not be removed; "
                    + "manual reconciliation required", keycloakId, compensationError);
        }
    }

    private void revert(String what, UserProfile profile, Runnable keycloakRevert, RuntimeException cause) {
        try {
            keycloakRevert.run();
            log.warn("Saving {} of profile {} failed; Keycloak change reverted", what, profile.getId(), cause);
        } catch (RuntimeException revertError) {
            log.error("Saving {} of profile {} failed and the Keycloak change (keycloakId={}) could not be reverted; "
                    + "manual reconciliation required", what, profile.getId(), profile.getKeycloakId(), revertError);
        }
    }

    static String likePattern(String search) {
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
