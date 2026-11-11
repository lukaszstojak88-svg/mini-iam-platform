package pl.stojak.miniiam.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import pl.stojak.miniiam.auth.domain.AppRole;
import pl.stojak.miniiam.auth.dto.response.RoleResponse;
import pl.stojak.miniiam.auth.exception.DomainException;
import pl.stojak.miniiam.auth.exception.ErrorCode;
import pl.stojak.miniiam.auth.integration.audit.AuditEventPublisher;
import pl.stojak.miniiam.auth.integration.audit.AuditEventRequest;
import pl.stojak.miniiam.auth.integration.audit.AuditEventType;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakAdminClient;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakRole;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakUser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoleServiceTest {

    private static final String ACTOR = "kc-admin";
    private static final String ACTOR_NAME = "admin";
    private static final String TARGET = "kc-target";

    private static final KeycloakRole ADMIN = new KeycloakRole("r-admin", "ADMIN", "Admin");
    private static final KeycloakRole MANAGER = new KeycloakRole("r-manager", "MANAGER", "Manager");
    private static final KeycloakRole USER = new KeycloakRole("r-user", "USER", "User");
    private static final KeycloakRole AUDITOR = new KeycloakRole("r-auditor", "AUDITOR", "Auditor");
    private static final KeycloakRole SERVICE = new KeycloakRole("r-service", "SERVICE", "Technical");
    private static final KeycloakRole DEFAULT_ROLES = new KeycloakRole("r-def", "default-roles-mini-iam", "Default");

    private KeycloakAdminClient keycloak;
    private AuditEventPublisher auditEventPublisher;
    private RoleService service;

    @BeforeEach
    void setUp() {
        keycloak = mock(KeycloakAdminClient.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        service = new RoleService(keycloak, auditEventPublisher);
    }

    private static KeycloakUser user(String id, boolean enabled) {
        return new KeycloakUser(id, "name-" + id, enabled);
    }

    private void targetExistsWithEffectiveRoles(KeycloakRole... roles) {
        when(keycloak.findUser(TARGET)).thenReturn(Optional.of(user(TARGET, true)));
        when(keycloak.getEffectiveRealmRoles(TARGET)).thenReturn(List.of(roles));
    }

    private AuditEventRequest publishedEvent() {
        ArgumentCaptor<AuditEventRequest> event = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventPublisher).publish(event.capture());
        return event.getValue();
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((DomainException) e).getCode();
    }

    // ---------------------------------------------------------------- listing

    @Test
    void listsOnlyApplicationRolesInDomainOrderWithAssignableFlag() {
        when(keycloak.listRealmRoles()).thenReturn(List.of(SERVICE, AUDITOR, DEFAULT_ROLES, USER,
                new KeycloakRole("x", "offline_access", null), MANAGER, ADMIN));

        List<RoleResponse> roles = service.listRoles();

        assertThat(roles).extracting(RoleResponse::name)
                .containsExactly(AppRole.ADMIN, AppRole.MANAGER, AppRole.USER, AppRole.AUDITOR);
        assertThat(roles).filteredOn(r -> !r.assignable()).extracting(RoleResponse::name).containsExactly(AppRole.USER);
        assertThat(roles.getFirst().description()).isEqualTo("Admin");
    }

    @Test
    void userRolesAreEffectiveApplicationRolesOnly() {
        targetExistsWithEffectiveRoles(DEFAULT_ROLES, USER, MANAGER, new KeycloakRole("x", "offline_access", null));

        assertThat(service.getUserRoles(TARGET).roles()).containsExactly(AppRole.MANAGER, AppRole.USER);
    }

    @Test
    void readingRolesOfMissingUserGives404() {
        when(keycloak.findUser(TARGET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserRoles(TARGET))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // ---------------------------------------------------------------- assigning

    @Test
    void assignsRoleAndReturnsFreshRoles() {
        when(keycloak.findUser(TARGET)).thenReturn(Optional.of(user(TARGET, true)));
        when(keycloak.getEffectiveRealmRoles(TARGET))
                .thenReturn(List.of(DEFAULT_ROLES, USER))
                .thenReturn(List.of(DEFAULT_ROLES, USER, MANAGER));
        when(keycloak.findRealmRole("MANAGER")).thenReturn(Optional.of(MANAGER));

        var result = service.assignRole(TARGET, "MANAGER", ACTOR, ACTOR_NAME);

        verify(keycloak).addRealmRole(TARGET, MANAGER);
        assertThat(result.userId()).isEqualTo(TARGET);
        assertThat(result.roles()).containsExactly(AppRole.MANAGER, AppRole.USER);
        AuditEventRequest event = publishedEvent();
        assertThat(event.eventType()).isEqualTo(AuditEventType.ROLE_ASSIGNED);
        assertThat(event.actorUserId()).isEqualTo(ACTOR);
        assertThat(event.actorUsername()).isEqualTo(ACTOR_NAME);
        assertThat(event.targetUserId()).isEqualTo(TARGET);
        assertThat(event.targetUsername()).isEqualTo("name-" + TARGET);
        assertThat(event.details()).isEqualTo(Map.of("roleName", "MANAGER"));
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void failedAuditDoesNotFailTheAssignment() {
        when(keycloak.findUser(TARGET)).thenReturn(Optional.of(user(TARGET, true)));
        when(keycloak.getEffectiveRealmRoles(TARGET))
                .thenReturn(List.of(USER))
                .thenReturn(List.of(USER, AUDITOR));
        when(keycloak.findRealmRole("AUDITOR")).thenReturn(Optional.of(AUDITOR));
        when(auditEventPublisher.publish(any())).thenReturn(false);

        var result = service.assignRole(TARGET, "AUDITOR", ACTOR, ACTOR_NAME);

        assertThat(result.roles()).containsExactly(AppRole.USER, AppRole.AUDITOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SERVICE", "default-roles-mini-iam", "manager", "SUPERUSER"})
    void onlyAdminManagerAuditorCanBeAssignedOrRemoved(String roleName) {
        assertThatThrownBy(() -> service.assignRole(TARGET, roleName, ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ROLE_NOT_ASSIGNABLE));
        assertThatThrownBy(() -> service.removeRole(TARGET, roleName, ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ROLE_NOT_ASSIGNABLE));
        verifyNoInteractions(keycloak);
    }

    @Test
    void cannotChangeOwnRoles() {
        assertThatThrownBy(() -> service.assignRole(ACTOR, "AUDITOR", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.SELF_ROLE_CHANGE));
        assertThatThrownBy(() -> service.removeRole(ACTOR, "ADMIN", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.SELF_ROLE_CHANGE));
        verifyNoInteractions(keycloak);
    }

    @Test
    void ownAccountIsRecognisedByCanonicalKeycloakId() {
        // A path variant (e.g. different letter case) that Keycloak resolves to the caller's own account.
        when(keycloak.findUser("KC-ADMIN")).thenReturn(Optional.of(user(ACTOR, true)));

        assertThatThrownBy(() -> service.removeRole("KC-ADMIN", "ADMIN", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.SELF_ROLE_CHANGE));
        assertThatThrownBy(() -> service.assignRole("KC-ADMIN", "AUDITOR", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.SELF_ROLE_CHANGE));
        verify(keycloak, never()).removeRealmRole(anyString(), any());
        verify(keycloak, never()).addRealmRole(anyString(), any());
    }

    @Test
    void assigningToMissingUserGives404() {
        when(keycloak.findUser(TARGET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRole(TARGET, "MANAGER", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void duplicateAssignmentIsConflict() {
        targetExistsWithEffectiveRoles(USER, MANAGER);

        assertThatThrownBy(() -> service.assignRole(TARGET, "MANAGER", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ROLE_ALREADY_ASSIGNED));
        verify(keycloak, never()).addRealmRole(anyString(), any());
    }

    @Test
    void roleMissingInRealmGives404() {
        targetExistsWithEffectiveRoles(USER);
        when(keycloak.findRealmRole("AUDITOR")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRole(TARGET, "AUDITOR", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ROLE_NOT_FOUND));
    }

    @Test
    void serviceAccountsAreProtected() {
        when(keycloak.findUser(TARGET)).thenReturn(Optional.of(new KeycloakUser(TARGET, "svc", true)));
        when(keycloak.getEffectiveRealmRoles(TARGET)).thenReturn(List.of(SERVICE));

        assertThatThrownBy(() -> service.assignRole(TARGET, "ADMIN", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.SERVICE_ACCOUNT_PROTECTED));
        assertThatThrownBy(() -> service.removeRole(TARGET, "ADMIN", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.SERVICE_ACCOUNT_PROTECTED));
        verify(keycloak, never()).addRealmRole(anyString(), any());
        verify(keycloak, never()).removeRealmRole(anyString(), any());
    }

    @Test
    void accountWithServiceRoleIsProtectedEvenWithoutClientLink() {
        targetExistsWithEffectiveRoles(USER, SERVICE);

        assertThatThrownBy(() -> service.assignRole(TARGET, "MANAGER", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.SERVICE_ACCOUNT_PROTECTED));
    }

    // ---------------------------------------------------------------- removing

    @Test
    void removesDirectlyAssignedRole() {
        targetExistsWithEffectiveRoles(USER, AUDITOR);
        when(keycloak.getDirectRealmRoles(TARGET)).thenReturn(List.of(DEFAULT_ROLES, AUDITOR));

        service.removeRole(TARGET, "AUDITOR", ACTOR, ACTOR_NAME);

        verify(keycloak).removeRealmRole(TARGET, AUDITOR);
        verify(keycloak, never()).getRealmRoleMembers(anyString());
        AuditEventRequest event = publishedEvent();
        assertThat(event.eventType()).isEqualTo(AuditEventType.ROLE_REMOVED);
        assertThat(event.targetUserId()).isEqualTo(TARGET);
        assertThat(event.targetUsername()).isEqualTo("name-" + TARGET);
        assertThat(event.details()).isEqualTo(Map.of("roleName", "AUDITOR"));
    }

    @Test
    void removingRoleThatIsNotAssignedGives404() {
        targetExistsWithEffectiveRoles(USER);
        when(keycloak.getDirectRealmRoles(TARGET)).thenReturn(List.of(DEFAULT_ROLES));

        assertThatThrownBy(() -> service.removeRole(TARGET, "MANAGER", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ROLE_NOT_ASSIGNED));
    }

    @Test
    void lastActiveAdminKeepsAdminRole() {
        targetExistsWithEffectiveRoles(USER, ADMIN);
        when(keycloak.getDirectRealmRoles(TARGET)).thenReturn(List.of(DEFAULT_ROLES, ADMIN));
        // Only enabled direct members count; the disabled administrator does not.
        when(keycloak.getRealmRoleMembers("ADMIN")).thenReturn(List.of(user(TARGET, true), user("kc-old", false)));

        assertThatThrownBy(() -> service.removeRole(TARGET, "ADMIN", ACTOR, ACTOR_NAME))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.LAST_ADMIN));
        verify(keycloak, never()).removeRealmRole(anyString(), any());
        // Rejected operations are not audited.
        verifyNoInteractions(auditEventPublisher);
    }

    @Test
    void adminRoleCanBeRemovedWhenAnotherActiveAdminExists() {
        targetExistsWithEffectiveRoles(USER, ADMIN);
        when(keycloak.getDirectRealmRoles(TARGET)).thenReturn(List.of(ADMIN));
        when(keycloak.getRealmRoleMembers("ADMIN")).thenReturn(List.of(user(TARGET, true), user(ACTOR, true)));

        service.removeRole(TARGET, "ADMIN", ACTOR, ACTOR_NAME);

        verify(keycloak).removeRealmRole(TARGET, ADMIN);
    }

    @Test
    void adminRoleCanBeRemovedFromDisabledAccount() {
        when(keycloak.findUser(TARGET)).thenReturn(Optional.of(user(TARGET, false)));
        when(keycloak.getEffectiveRealmRoles(TARGET)).thenReturn(List.of(USER, ADMIN));
        when(keycloak.getDirectRealmRoles(TARGET)).thenReturn(List.of(ADMIN));
        when(keycloak.getRealmRoleMembers("ADMIN")).thenReturn(List.of(user(TARGET, false), user(ACTOR, true)));

        service.removeRole(TARGET, "ADMIN", ACTOR, ACTOR_NAME);

        verify(keycloak).removeRealmRole(TARGET, ADMIN);
    }
}
