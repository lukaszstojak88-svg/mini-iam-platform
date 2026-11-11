package pl.stojak.miniiam.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import pl.stojak.miniiam.user.domain.UserProfile;
import pl.stojak.miniiam.user.dto.request.CreateUserRequest;
import pl.stojak.miniiam.user.dto.request.UpdateProfileRequest;
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
import pl.stojak.miniiam.user.integration.keycloak.KeycloakUnavailableException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakUser;
import pl.stojak.miniiam.user.integration.keycloak.NewKeycloakUser;
import pl.stojak.miniiam.user.repository.UserProfileRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Actor ADMIN = new Actor("kc-admin", "admin", Set.of("ADMIN", "USER"));
    private static final Actor MANAGER = new Actor("kc-manager", "manager", Set.of("MANAGER", "USER"));
    /** Another administrator acting on the account kc-admin (own-account changes are a separate rule). */
    private static final Actor OTHER_ADMIN = new Actor("kc-admin-2", "admin2", Set.of("ADMIN", "USER"));

    @Mock
    private UserProfileRepository repository;

    @Mock
    private KeycloakAdminClient keycloak;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, keycloak, auditEventPublisher);
    }

    private static UserProfile profile(String keycloakId, boolean enabled) {
        return new UserProfile(keycloakId, "john.doe", "john@example.com", "John", "Doe", enabled);
    }

    private static KeycloakUser account(String id, boolean enabled) {
        return new KeycloakUser(id, "user-" + id, id + "@example.com", null, null, enabled, List.of());
    }

    // ---------------------------------------------------------------- create

    @Test
    void createNormalizesDataAndSavesProfileWithKeycloakId() {
        when(keycloak.createUser(any())).thenReturn("kc-new");
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse created = service.create(
                new CreateUserRequest("John.Doe", "John@Example.com", "John", "Doe", "Secret123!"), ADMIN);

        ArgumentCaptor<NewKeycloakUser> sent = ArgumentCaptor.forClass(NewKeycloakUser.class);
        verify(keycloak).createUser(sent.capture());
        assertThat(sent.getValue().username()).isEqualTo("john.doe");
        assertThat(sent.getValue().email()).isEqualTo("john@example.com");
        assertThat(sent.getValue().temporaryPassword()).isEqualTo("Secret123!");
        assertThat(created.keycloakId()).isEqualTo("kc-new");
        assertThat(created.username()).isEqualTo("john.doe");
        assertThat(created.enabled()).isTrue();
    }

    @Test
    void createCompensatesKeycloakAccountWhenProfileSaveFails() {
        when(keycloak.createUser(any())).thenReturn("kc-new");
        when(repository.save(any(UserProfile.class))).thenThrow(new DataIntegrityViolationException("db down"));

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("john", "john@example.com", null, null, "Secret123!"), ADMIN))
                .isInstanceOf(UserSyncException.class);

        verify(keycloak).deleteUser("kc-new");
    }

    @Test
    void createStillReportsErrorWhenCompensationFails() {
        when(keycloak.createUser(any())).thenReturn("kc-new");
        when(repository.save(any(UserProfile.class))).thenThrow(new DataIntegrityViolationException("db down"));
        when(keycloak.deleteUser("kc-new")).thenThrow(new KeycloakUnavailableException("deleteUser", null));

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("john", "john@example.com", null, null, "Secret123!"), ADMIN))
                .isInstanceOf(UserSyncException.class);
    }

    // ---------------------------------------------------------------- update

    @Test
    void updateChangesKeycloakThenLocalProfile() {
        UserProfile existing = profile("kc-1", true);
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse updated = service.update(existing.getId(),
                new UpdateProfileRequest("New@Example.com", "Johnny", null), ADMIN);

        verify(keycloak).updateProfile("kc-1", "new@example.com", "Johnny", null, true);
        assertThat(updated.email()).isEqualTo("new@example.com");
        assertThat(updated.firstName()).isEqualTo("Johnny");
        assertThat(updated.lastName()).isNull();
    }

    @Test
    void updateRevertsKeycloakWhenLocalSaveFails() {
        UserProfile existing = profile("kc-1", true);
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(UserProfile.class))).thenThrow(new DataIntegrityViolationException("db down"));

        assertThatThrownBy(() -> service.update(existing.getId(),
                new UpdateProfileRequest("new@example.com", "Johnny", "D"), ADMIN))
                .isInstanceOf(UserSyncException.class);

        verify(keycloak).updateProfile("kc-1", "new@example.com", "Johnny", "D", true);
        verify(keycloak).updateProfile("kc-1", "john@example.com", "John", "Doe", false);
    }

    @Test
    void managerCannotModifyAdministratorAccount() {
        UserProfile adminProfile = profile("kc-other-admin", true);
        when(repository.findById(adminProfile.getId())).thenReturn(Optional.of(adminProfile));
        when(keycloak.getEffectiveRealmRoles("kc-other-admin")).thenReturn(List.of("ADMIN", "USER"));

        assertThatThrownBy(() -> service.update(adminProfile.getId(),
                new UpdateProfileRequest("evil@example.com", null, null), MANAGER))
                .isInstanceOf(OperationForbiddenException.class)
                .extracting("code").isEqualTo(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        verify(keycloak, never()).updateProfile(anyString(), anyString(), any(), any(), anyBoolean());
    }

    @Test
    void managerCanModifyRegularUser() {
        UserProfile user = profile("kc-user", true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.getEffectiveRealmRoles("kc-user")).thenReturn(List.of("USER"));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(user.getId(), new UpdateProfileRequest("x@example.com", null, null), MANAGER);

        verify(keycloak).updateProfile("kc-user", "x@example.com", null, null, true);
    }

    // ---------------------------------------------------------------- status / delete: last admin rule

    @Test
    void lastActiveAdminCannotBeDeactivated() {
        UserProfile admin = profile("kc-admin", true);
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(keycloak.getRealmRoleMembers("ADMIN"))
                .thenReturn(List.of(account("kc-admin", true), account("kc-disabled-admin", false)));

        assertThatThrownBy(() -> service.updateStatus(admin.getId(), false, OTHER_ADMIN))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo(ErrorCode.LAST_ADMIN);
        verify(keycloak, never()).setEnabled(anyString(), eq(false));
    }

    @Test
    void adminCanBeDeactivatedWhenAnotherActiveAdminExists() {
        UserProfile admin = profile("kc-admin", true);
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(keycloak.getRealmRoleMembers("ADMIN"))
                .thenReturn(List.of(account("kc-admin", true), account("kc-admin-2", true)));
        when(keycloak.findUser("kc-admin")).thenReturn(Optional.of(account("kc-admin", true)));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = service.updateStatus(admin.getId(), false, OTHER_ADMIN);

        verify(keycloak).setEnabled("kc-admin", false);
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void activationDoesNotCheckAdmins() {
        UserProfile user = profile("kc-user", false);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.findUser("kc-user")).thenReturn(Optional.of(account("kc-user", false)));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(user.getId(), true, ADMIN);

        verify(keycloak, never()).getRealmRoleMembers(anyString());
        verify(keycloak).setEnabled("kc-user", true);
    }

    @Test
    void statusChangeIsRevertedInKeycloakWhenLocalSaveFails() {
        UserProfile user = profile("kc-user", true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.getRealmRoleMembers("ADMIN")).thenReturn(List.of());
        when(keycloak.findUser("kc-user")).thenReturn(Optional.of(account("kc-user", true)));
        when(repository.save(any(UserProfile.class))).thenThrow(new DataIntegrityViolationException("db down"));

        assertThatThrownBy(() -> service.updateStatus(user.getId(), false, ADMIN))
                .isInstanceOf(UserSyncException.class);

        verify(keycloak).setEnabled("kc-user", false);
        verify(keycloak).setEnabled("kc-user", true);
    }

    @Test
    void statusRevertUsesKeycloakStateNotStaleLocalFlag() {
        UserProfile user = profile("kc-user", true); // local says enabled, Keycloak says disabled
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.findUser("kc-user")).thenReturn(Optional.of(account("kc-user", false)));
        when(repository.save(any(UserProfile.class))).thenThrow(new DataIntegrityViolationException("db down"));

        assertThatThrownBy(() -> service.updateStatus(user.getId(), true, ADMIN))
                .isInstanceOf(UserSyncException.class);

        verify(keycloak).setEnabled("kc-user", true);
        verify(keycloak).setEnabled("kc-user", false);
    }

    @Test
    void ambiguousCreateRemovesAccountThatKeycloakCreatedAnyway() {
        when(keycloak.createUser(any())).thenThrow(new KeycloakUnavailableException("createUser", null));
        when(keycloak.findUserIdByUsername("john")).thenReturn(Optional.of("kc-orphan"));

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("john", "john@example.com", null, null, "Secret123!"), ADMIN))
                .isInstanceOf(KeycloakUnavailableException.class);

        verify(keycloak).deleteUser("kc-orphan");
        verify(repository, never()).save(any());
    }

    @Test
    void lastActiveAdminCannotBeDeleted() {
        UserProfile admin = profile("kc-admin", true);
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(keycloak.getRealmRoleMembers("ADMIN")).thenReturn(List.of(account("kc-admin", true)));

        assertThatThrownBy(() -> service.delete(admin.getId(), OTHER_ADMIN))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(keycloak, never()).deleteUser(anyString());
    }

    @Test
    void deleteRemovesKeycloakAccountAndProfileAndToleratesMissingAccount() {
        UserProfile user = profile("kc-user", true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.getRealmRoleMembers("ADMIN")).thenReturn(List.of(account("kc-admin", true)));
        when(keycloak.deleteUser("kc-user")).thenReturn(false);

        service.delete(user.getId(), ADMIN);

        verify(repository).delete(user);
    }

    @Test
    void ownAccountCannotBeDeactivatedOrDeleted() {
        UserProfile mine = profile("kc-admin", true);
        when(repository.findById(mine.getId())).thenReturn(Optional.of(mine));

        assertThatThrownBy(() -> service.updateStatus(mine.getId(), false, ADMIN))
                .isInstanceOf(OperationForbiddenException.class)
                .satisfies(e -> assertThat(((OperationForbiddenException) e).getCode())
                        .isEqualTo(ErrorCode.SELF_ACCOUNT_PROTECTED));
        assertThatThrownBy(() -> service.delete(mine.getId(), ADMIN))
                .isInstanceOf(OperationForbiddenException.class)
                .satisfies(e -> assertThat(((OperationForbiddenException) e).getCode())
                        .isEqualTo(ErrorCode.SELF_ACCOUNT_PROTECTED));

        verify(keycloak, never()).setEnabled(anyString(), anyBoolean());
        verify(keycloak, never()).deleteUser(anyString());
        verifyNoInteractions(auditEventPublisher);
    }

    @Test
    void deleteReportsErrorWhenLocalDeleteFails() {
        UserProfile user = profile("kc-user", true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.getRealmRoleMembers("ADMIN")).thenReturn(List.of());
        when(keycloak.deleteUser("kc-user")).thenReturn(true);
        doThrow(new DataIntegrityViolationException("db down")).when(repository).delete(user);

        assertThatThrownBy(() -> service.delete(user.getId(), ADMIN)).isInstanceOf(UserSyncException.class);
    }

    @Test
    void unknownProfileGives404() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(java.util.UUID.randomUUID())).isInstanceOf(UserNotFoundException.class);
    }

    // ---------------------------------------------------------------- get: status reconciliation

    @Test
    void getAlignsLocalStatusWithKeycloak() {
        UserProfile user = profile("kc-user", true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.findUser("kc-user")).thenReturn(Optional.of(account("kc-user", false)));

        assertThat(service.get(user.getId()).enabled()).isFalse();
        verify(repository).updateEnabled(eq(user.getId()), eq(false), any());
        verify(repository, never()).save(any());
    }

    @Test
    void getReturnsLocalDataWhenKeycloakIsUnavailable() {
        UserProfile user = profile("kc-user", true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.findUser("kc-user")).thenThrow(new KeycloakUnavailableException("findUser", null));

        assertThat(service.get(user.getId()).enabled()).isTrue();
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------- own profile (JIT)

    @Test
    void ownProfileIsProvisionedJustInTime() {
        when(repository.findByKeycloakId("kc-admin")).thenReturn(Optional.empty());
        when(keycloak.findUser("kc-admin")).thenReturn(Optional.of(
                new KeycloakUser("kc-admin", "admin", "admin@example.com", "Admin", "Mini IAM", true, List.of())));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse me = service.getOwnProfile(ADMIN);

        assertThat(me.username()).isEqualTo("admin");
        assertThat(me.keycloakId()).isEqualTo("kc-admin");
    }

    @Test
    void concurrentProvisioningReusesExistingProfile() {
        UserProfile existing = profile("kc-admin", true);
        when(repository.findByKeycloakId("kc-admin")).thenReturn(Optional.empty(), Optional.of(existing));
        when(keycloak.findUser("kc-admin")).thenReturn(Optional.of(account("kc-admin", true)));
        when(repository.save(any(UserProfile.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.getOwnProfile(ADMIN).id()).isEqualTo(existing.getId());
    }

    @Test
    void serviceAccountsHaveNoProfile() {
        assertThatThrownBy(() -> service.getOwnProfile(new Actor("kc-sa", "sa", Set.of("SERVICE"))))
                .isInstanceOf(UserNotFoundException.class);
        verify(keycloak, never()).findUser(anyString());
    }

    @Test
    void updateOwnProfileChangesEmailAndNames() {
        UserProfile mine = profile("kc-user", true);
        when(repository.findByKeycloakId("kc-user")).thenReturn(Optional.of(mine));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse updated = service.updateOwnProfile(
                new UpdateProfileRequest("me@example.com", "Me", "Myself"), new Actor("kc-user", "user", Set.of("USER")));

        verify(keycloak).updateProfile("kc-user", "me@example.com", "Me", "Myself", true);
        assertThat(updated.email()).isEqualTo("me@example.com");
    }

    // ---------------------------------------------------------------- audit (best effort, spec §6.3, §7)

    private AuditEventRequest publishedEvent() {
        ArgumentCaptor<AuditEventRequest> event = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventPublisher).publish(event.capture());
        return event.getValue();
    }

    @Test
    void createIsAuditedWithUsernameOnly() {
        when(keycloak.createUser(any())).thenReturn("kc-new");
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreateUserRequest("John.Doe", "john@example.com", "John", "Doe", "Secret123!"), MANAGER);

        AuditEventRequest event = publishedEvent();
        assertThat(event.eventType()).isEqualTo(AuditEventType.USER_CREATED);
        assertThat(event.actorUserId()).isEqualTo("kc-manager");
        assertThat(event.actorUsername()).isEqualTo("manager");
        assertThat(event.targetUserId()).isEqualTo("kc-new");
        assertThat(event.targetUsername()).isEqualTo("john.doe");
        assertThat(event.occurredAt()).isNotNull();
        // Never the temporary password, nor the e-mail.
        assertThat(event.details()).isEqualTo(Map.of("username", "john.doe"));
    }

    @Test
    void failedAuditDoesNotFailTheOperation() {
        when(keycloak.createUser(any())).thenReturn("kc-new");
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditEventPublisher.publish(any())).thenReturn(false);

        UserResponse created = service.create(
                new CreateUserRequest("john", "john@example.com", null, null, "Secret123!"), ADMIN);

        assertThat(created.keycloakId()).isEqualTo("kc-new");
    }

    @Test
    void failedOperationIsNotAudited() {
        when(keycloak.createUser(any())).thenReturn("kc-new");
        when(repository.save(any(UserProfile.class))).thenThrow(new DataIntegrityViolationException("db down"));

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("john", "john@example.com", null, null, "Secret123!"), ADMIN))
                .isInstanceOf(UserSyncException.class);

        verifyNoInteractions(auditEventPublisher);
    }

    @Test
    void updateIsAuditedWithNamesOfChangedFields() {
        UserProfile existing = profile("kc-1", true);
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(existing.getId(), new UpdateProfileRequest("John@Example.com", "Johnny", null), ADMIN);

        AuditEventRequest event = publishedEvent();
        assertThat(event.eventType()).isEqualTo(AuditEventType.USER_UPDATED);
        assertThat(event.targetUserId()).isEqualTo("kc-1");
        // The e-mail differs only in case (normalized) - not a change.
        assertThat(event.details()).isEqualTo(Map.of("changedFields", List.of("firstName", "lastName")));
    }

    @Test
    void ownProfileUpdateIsAuditedWithActorAsTarget() {
        UserProfile mine = profile("kc-user", true);
        when(repository.findByKeycloakId("kc-user")).thenReturn(Optional.of(mine));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateOwnProfile(new UpdateProfileRequest("me@example.com", "John", "Doe"),
                new Actor("kc-user", "user", Set.of("USER")));

        AuditEventRequest event = publishedEvent();
        assertThat(event.actorUserId()).isEqualTo("kc-user");
        assertThat(event.targetUserId()).isEqualTo("kc-user");
        assertThat(event.details()).isEqualTo(Map.of("changedFields", List.of("email")));
    }

    @Test
    void updateWithoutChangesIsNotAudited() {
        UserProfile existing = profile("kc-1", true);
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(existing.getId(), new UpdateProfileRequest("john@example.com", "John", "Doe"), ADMIN);

        verifyNoInteractions(auditEventPublisher);
    }

    @Test
    void deactivationAndActivationAreAudited() {
        UserProfile user = profile("kc-user", true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.getRealmRoleMembers("ADMIN")).thenReturn(List.of(account("kc-admin", true)));
        when(keycloak.findUser("kc-user"))
                .thenReturn(Optional.of(account("kc-user", true)))
                .thenReturn(Optional.of(account("kc-user", false)));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(user.getId(), false, ADMIN);
        service.updateStatus(user.getId(), true, ADMIN);

        ArgumentCaptor<AuditEventRequest> events = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventPublisher, times(2)).publish(events.capture());
        assertThat(events.getAllValues()).extracting(AuditEventRequest::eventType)
                .containsExactly(AuditEventType.USER_DEACTIVATED, AuditEventType.USER_ACTIVATED);
        assertThat(events.getAllValues()).allSatisfy(event -> {
            assertThat(event.targetUserId()).isEqualTo("kc-user");
            assertThat(event.details()).isEmpty();
        });
    }

    @Test
    void settingTheCurrentStatusIsNotAudited() {
        UserProfile user = profile("kc-user", true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.findUser("kc-user")).thenReturn(Optional.of(account("kc-user", true)));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(user.getId(), true, ADMIN);

        verifyNoInteractions(auditEventPublisher);
    }

    @Test
    void deleteIsAuditedWithUsername() {
        UserProfile user = profile("kc-user", true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloak.getRealmRoleMembers("ADMIN")).thenReturn(List.of(account("kc-admin", true)));
        when(keycloak.deleteUser("kc-user")).thenReturn(true);

        service.delete(user.getId(), ADMIN);

        AuditEventRequest event = publishedEvent();
        assertThat(event.eventType()).isEqualTo(AuditEventType.USER_DELETED);
        assertThat(event.actorUserId()).isEqualTo("kc-admin");
        assertThat(event.actorUsername()).isEqualTo("admin");
        assertThat(event.targetUserId()).isEqualTo("kc-user");
        assertThat(event.targetUsername()).isEqualTo("john.doe");
        assertThat(event.details()).isEqualTo(Map.of("username", "john.doe"));
    }

    @Test
    void justInTimeProvisioningIsNotAudited() {
        when(repository.findByKeycloakId("kc-new")).thenReturn(Optional.empty());
        when(keycloak.findUser("kc-new")).thenReturn(Optional.of(account("kc-new", true)));
        when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.getOwnProfile(new Actor("kc-new", "new", Set.of("USER")));

        verifyNoInteractions(auditEventPublisher);
    }

    // ---------------------------------------------------------------- list

    @Test
    void listRejectsUnknownSortProperty() {
        assertThatThrownBy(() -> service.list(null, PageRequest.of(0, 20, Sort.by("keycloakId"))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void listRejectsPageSizeAboveLimit() {
        assertThatThrownBy(() -> service.list(null, PageRequest.of(0, 101)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("'size' must not exceed 100.");
        verifyNoInteractions(repository);
    }

    @Test
    void listAcceptsPageSizeAtLimit() {
        PageRequest stable = PageRequest.of(0, 100, Sort.by("id"));
        when(repository.findAll(stable)).thenReturn(new PageImpl<>(List.of(), stable, 0));

        assertThat(service.list(null, PageRequest.of(0, 100)).size()).isEqualTo(100);
    }

    @Test
    void listUsesEscapedSearchPattern() {
        PageRequest page = PageRequest.of(0, 20, Sort.by("username"));
        PageRequest stable = PageRequest.of(0, 20, Sort.by("username").and(Sort.by("id")));
        when(repository.search("%jo\\%h\\_n%", stable)).thenReturn(new PageImpl<>(List.of(), stable, 0));

        assertThat(service.list("  Jo%h_n ", page).totalElements()).isZero();
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(UserService.likePattern("a\\b%c_d")).isEqualTo("%a\\\\b\\%c\\_d%");
    }
}
