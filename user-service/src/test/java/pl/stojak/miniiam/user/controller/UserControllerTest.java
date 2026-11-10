package pl.stojak.miniiam.user.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pl.stojak.miniiam.user.config.CorrelationIdFilter;
import pl.stojak.miniiam.user.dto.response.PageResponse;
import pl.stojak.miniiam.user.dto.response.UserResponse;
import pl.stojak.miniiam.user.exception.BusinessRuleViolationException;
import pl.stojak.miniiam.user.exception.ErrorCode;
import pl.stojak.miniiam.user.exception.GlobalExceptionHandler;
import pl.stojak.miniiam.user.exception.UserNotFoundException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakConflictException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakUnavailableException;
import pl.stojak.miniiam.user.security.SecurityConfig;
import pl.stojak.miniiam.user.service.Actor;
import pl.stojak.miniiam.user.service.UserService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, GlobalExceptionHandler.class})
class UserControllerTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String CREATE_BODY = """
            {"username":"john.doe","email":"john@example.com","firstName":"John","lastName":"Doe",
             "temporaryPassword":"Secret123!"}""";
    private static final String UPDATE_BODY = """
            {"email":"john@example.com","firstName":"John","lastName":"Doe"}""";
    private static final String STATUS_BODY = """
            {"enabled":false}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static RequestPostProcessor as(String... roles) {
        return jwt().jwt(token -> token.subject("kc-caller"))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private static UserResponse user() {
        Instant now = Instant.parse("2026-11-10T09:00:00Z");
        return new UserResponse(ID, "kc-1", "john.doe", "john@example.com", "John", "Doe", true, now, now);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---------------------------------------------------------------- authorization matrix (spec §2.1)

    static Stream<Arguments> endpoints() {
        return Stream.of(
                // method, path, body, roles allowed
                Arguments.of(HttpMethod.GET, "/api/users", null, List.of("ADMIN", "MANAGER")),
                Arguments.of(HttpMethod.GET, "/api/users/" + ID, null, List.of("ADMIN", "MANAGER")),
                Arguments.of(HttpMethod.POST, "/api/users", CREATE_BODY, List.of("ADMIN", "MANAGER")),
                Arguments.of(HttpMethod.PUT, "/api/users/" + ID, UPDATE_BODY, List.of("ADMIN", "MANAGER")),
                Arguments.of(HttpMethod.PATCH, "/api/users/" + ID + "/status", STATUS_BODY, List.of("ADMIN")),
                Arguments.of(HttpMethod.DELETE, "/api/users/" + ID, null, List.of("ADMIN")),
                Arguments.of(HttpMethod.GET, "/api/users/me", null, List.of("ADMIN", "MANAGER", "USER", "AUDITOR")),
                Arguments.of(HttpMethod.PUT, "/api/users/me", UPDATE_BODY, List.of("ADMIN", "MANAGER", "USER")));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    void endpointsFollowRoleMatrix(HttpMethod method, String path, String body, List<String> allowed) throws Exception {
        stubServiceResponses();
        for (String role : List.of("ADMIN", "MANAGER", "USER", "AUDITOR")) {
            MockHttpServletRequestBuilder builder = request(method, path);
            if (body != null) {
                builder = json(builder, body);
            }
            int status = mockMvc.perform(builder.with(as(role))).andReturn().getResponse().getStatus();
            if (allowed.contains(role)) {
                assertThat(status).as("%s %s as %s", method, path, role).isBetween(200, 299);
            } else {
                assertThat(status).as("%s %s as %s", method, path, role).isEqualTo(403);
            }
        }
        MockHttpServletRequestBuilder anonymous = request(method, path);
        if (body != null) {
            anonymous = json(anonymous, body);
        }
        mockMvc.perform(anonymous).andExpect(status().isUnauthorized());
    }

    private void stubServiceResponses() {
        when(userService.list(any(), any())).thenReturn(new PageResponse<>(List.of(user()), 0, 20, 1, 1));
        when(userService.get(any())).thenReturn(user());
        when(userService.create(any(), any())).thenReturn(user());
        when(userService.update(any(), any(), any())).thenReturn(user());
        when(userService.updateStatus(any(), eq(false), any())).thenReturn(user());
        when(userService.getOwnProfile(any())).thenReturn(user());
        when(userService.updateOwnProfile(any(), any())).thenReturn(user());
    }

    // ---------------------------------------------------------------- behaviour

    @Test
    void createReturns201WithLocationAndNoPassword() throws Exception {
        when(userService.create(any(), any())).thenReturn(user());

        mockMvc.perform(json(post("/api/users"), CREATE_BODY).with(as("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/users/" + ID))
                .andExpect(jsonPath("$.keycloakId").value("kc-1"))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Secret123!"))));
    }

    @Test
    void actorIsBuiltFromTokenSubjectAndRoles() throws Exception {
        when(userService.create(any(), any())).thenReturn(user());

        mockMvc.perform(json(post("/api/users"), CREATE_BODY).with(as("MANAGER", "USER")))
                .andExpect(status().isCreated());

        verify(userService).create(any(), eq(new Actor("kc-caller", java.util.Set.of("MANAGER", "USER"))));
    }

    @Test
    void invalidCreateRequestGives400WithFieldErrors() throws Exception {
        mockMvc.perform(json(post("/api/users"), """
                        {"username":"a b","email":"not-an-email","temporaryPassword":"short"}""")
                        .with(as("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='username')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='temporaryPassword')]").exists());
    }

    @Test
    void malformedJsonGives400() throws Exception {
        mockMvc.perform(json(post("/api/users"), "{not json").with(as("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidUuidGives400() throws Exception {
        mockMvc.perform(get("/api/users/not-a-uuid").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooLongSearchGives400() throws Exception {
        mockMvc.perform(get("/api/users").param("search", "x".repeat(101)).with(as("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("search"));
    }

    @Test
    void pageSizeIsCappedAt100AndDefaultSortIsCreatedAtDesc() throws Exception {
        when(userService.list(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        mockMvc.perform(get("/api/users").param("size", "500").with(as("ADMIN"))).andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(userService).list(isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void domainErrorsAreMappedToProblemDetails() throws Exception {
        when(userService.get(ID)).thenThrow(new UserNotFoundException("missing"));
        mockMvc.perform(get("/api/users/" + ID).with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        when(userService.create(any(), any())).thenThrow(new KeycloakConflictException("createUser", null));
        mockMvc.perform(json(post("/api/users"), CREATE_BODY).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_OR_EMAIL_TAKEN"));

        when(userService.updateStatus(any(), eq(false), any()))
                .thenThrow(new BusinessRuleViolationException(ErrorCode.LAST_ADMIN, "last admin"));
        mockMvc.perform(json(request(HttpMethod.PATCH, "/api/users/" + ID + "/status"), STATUS_BODY).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void keycloakOutageGives503WithoutTechnicalDetails() throws Exception {
        when(userService.update(any(), any(), any()))
                .thenThrow(new KeycloakUnavailableException("updateProfile", new RuntimeException("Connection refused: keycloak:8080")));

        mockMvc.perform(json(put("/api/users/" + ID), UPDATE_BODY).with(as("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KEYCLOAK_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("keycloak:8080"))));
    }

    @Test
    void unexpectedErrorGives500WithoutStackTrace() throws Exception {
        when(userService.getOwnProfile(any())).thenThrow(new IllegalStateException("boom at com.example.Secret"));

        mockMvc.perform(get("/api/users/me").with(as("USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("boom"))));
    }
}
