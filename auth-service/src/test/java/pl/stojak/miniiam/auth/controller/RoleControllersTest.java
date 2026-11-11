package pl.stojak.miniiam.auth.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pl.stojak.miniiam.auth.config.CorrelationIdFilter;
import pl.stojak.miniiam.auth.domain.AppRole;
import pl.stojak.miniiam.auth.dto.response.RoleResponse;
import pl.stojak.miniiam.auth.dto.response.UserRolesResponse;
import pl.stojak.miniiam.auth.exception.DomainException;
import pl.stojak.miniiam.auth.exception.ErrorCode;
import pl.stojak.miniiam.auth.exception.GlobalExceptionHandler;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakUnavailableException;
import pl.stojak.miniiam.auth.security.SecurityConfig;
import pl.stojak.miniiam.auth.service.RoleService;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {RoleController.class, UserRoleController.class})
@Import({SecurityConfig.class, CorrelationIdFilter.class, GlobalExceptionHandler.class})
class RoleControllersTest {

    private static final String CALLER = "kc-caller";
    private static final String TARGET = "0b6f1c2e-1111-2222-3333-444455556666";
    private static final String ROLES_URL = "/api/users/" + TARGET + "/roles";
    private static final String ASSIGN_MANAGER = "{\"roleName\":\"MANAGER\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    private static RequestPostProcessor asSubject(String subject, String... roles) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private static RequestPostProcessor as(String... roles) {
        return asSubject(CALLER, roles);
    }

    // ---------------------------------------------------------------- GET /api/roles

    @Test
    void anonymousCallerGets401() throws Exception {
        mockMvc.perform(get("/api/roles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROLES_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ROLES_URL).contentType(MediaType.APPLICATION_JSON).content(ASSIGN_MANAGER))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ROLES_URL + "/MANAGER")).andExpect(status().isUnauthorized());
        verifyNoInteractions(roleService);
    }

    @Test
    void adminAndManagerListRoles() throws Exception {
        when(roleService.listRoles()).thenReturn(List.of(
                new RoleResponse(AppRole.ADMIN, "Admin", true), new RoleResponse(AppRole.USER, "Base", false)));

        mockMvc.perform(get("/api/roles").with(as("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ADMIN"))
                .andExpect(jsonPath("$[0].assignable").value(true))
                .andExpect(jsonPath("$[1].name").value("USER"))
                .andExpect(jsonPath("$[1].assignable").value(false))
                .andExpect(content().string(not(containsString("\"id\""))));
        mockMvc.perform(get("/api/roles").with(as("MANAGER", "USER"))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "AUDITOR", "SERVICE"})
    void otherRolesCannotListRoles(String role) throws Exception {
        mockMvc.perform(get("/api/roles").with(as(role)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------------------------------------------------------------- GET /api/users/{userId}/roles

    @Test
    void adminReadsAnyUsersRoles() throws Exception {
        when(roleService.getUserRoles(TARGET)).thenReturn(new UserRolesResponse(TARGET, List.of(AppRole.USER)));

        mockMvc.perform(get(ROLES_URL).with(as("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TARGET))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void userReadsOnlyOwnRoles() throws Exception {
        when(roleService.getUserRoles(TARGET)).thenReturn(new UserRolesResponse(TARGET, List.of(AppRole.USER)));

        mockMvc.perform(get(ROLES_URL).with(asSubject(TARGET, "USER"))).andExpect(status().isOk());
        mockMvc.perform(get(ROLES_URL).with(asSubject("someone-else", "USER"))).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "AUDITOR"})
    void managerAndAuditorCannotReadOtherUsersRoles(String role) throws Exception {
        mockMvc.perform(get(ROLES_URL).with(as(role, "USER"))).andExpect(status().isForbidden());
    }

    @Test
    void invalidUserIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/users/user_1/roles").with(as("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("userId"));
        mockMvc.perform(delete("/api/users/" + "a".repeat(65) + "/roles/MANAGER").with(as("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(roleService);
    }

    // ---------------------------------------------------------------- POST / DELETE

    @Test
    void adminAssignsRoleAndGetsCurrentRoles() throws Exception {
        when(roleService.assignRole(TARGET, "MANAGER", CALLER))
                .thenReturn(new UserRolesResponse(TARGET, List.of(AppRole.MANAGER, AppRole.USER)));

        mockMvc.perform(post(ROLES_URL).with(as("ADMIN", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(ASSIGN_MANAGER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"));
    }

    @Test
    void adminRemovesRoleWith204() throws Exception {
        mockMvc.perform(delete(ROLES_URL + "/MANAGER").with(as("ADMIN", "USER")))
                .andExpect(status().isNoContent());
        verify(roleService).removeRole(TARGET, "MANAGER", CALLER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "USER", "AUDITOR", "SERVICE"})
    void onlyAdminChangesRoles(String role) throws Exception {
        mockMvc.perform(post(ROLES_URL).with(as(role, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(ASSIGN_MANAGER))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ROLES_URL + "/MANAGER").with(as(role, "USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(roleService);
    }

    @Test
    void blankRoleNameFailsValidation() throws Exception {
        mockMvc.perform(post(ROLES_URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleName\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("roleName"));
    }

    @Test
    void malformedBodyIsBadRequestWithCode() throws Exception {
        mockMvc.perform(post(ROLES_URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---------------------------------------------------------------- error mapping

    @Test
    void domainErrorsBecomeProblemDetailsWithCode() throws Exception {
        when(roleService.assignRole(anyString(), anyString(), anyString()))
                .thenThrow(DomainException.badRequest(ErrorCode.ROLE_NOT_ASSIGNABLE, "Only the roles ..."));
        doThrow(DomainException.conflict(ErrorCode.LAST_ADMIN, "last admin"))
                .when(roleService).removeRole(TARGET, "ADMIN", CALLER);

        mockMvc.perform(post(ROLES_URL).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleName\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"));
        mockMvc.perform(delete(ROLES_URL + "/ADMIN").with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void dependencyFailuresDoNotLeakDetails() throws Exception {
        when(roleService.getUserRoles(TARGET))
                .thenThrow(new KeycloakUnavailableException("getEffectiveRealmRoles", new RuntimeException("10.0.0.5")));

        mockMvc.perform(get(ROLES_URL).with(as("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KEYCLOAK_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("10.0.0.5"))));
    }
}
