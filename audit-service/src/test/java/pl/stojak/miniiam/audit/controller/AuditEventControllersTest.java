package pl.stojak.miniiam.audit.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pl.stojak.miniiam.audit.config.CorrelationIdFilter;
import pl.stojak.miniiam.audit.domain.AuditEventType;
import pl.stojak.miniiam.audit.dto.request.RecordAuditEventRequest;
import pl.stojak.miniiam.audit.dto.response.AuditEventResponse;
import pl.stojak.miniiam.audit.dto.response.PageResponse;
import pl.stojak.miniiam.audit.exception.AuditEventNotFoundException;
import pl.stojak.miniiam.audit.exception.GlobalExceptionHandler;
import pl.stojak.miniiam.audit.exception.InvalidRequestException;
import pl.stojak.miniiam.audit.security.SecurityConfig;
import pl.stojak.miniiam.audit.service.AuditEventService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuditEventController.class, InternalAuditEventController.class})
@Import({SecurityConfig.class, CorrelationIdFilter.class, GlobalExceptionHandler.class})
class AuditEventControllersTest {

    private static final String LIST_URL = "/api/audit-events";
    private static final String INTERNAL_URL = "/internal/api/audit-events";
    private static final UUID EVENT_ID = UUID.fromString("7d0f4f43-56a4-4c1e-9d0b-0a6a7e2b6c11");
    private static final String VALID_EVENT = """
            {"eventType":"ROLE_ASSIGNED","actorUserId":"kc-admin-1","actorUsername":"admin",
             "targetUserId":"kc-user-2","targetUsername":"jan","details":{"roleName":"MANAGER"},"occurredAt":"2026-11-10T09:00:00Z"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditEventService auditEventService;

    private static RequestPostProcessor as(String... roles) {
        return jwt().jwt(token -> token.subject("kc-caller"))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private static AuditEventResponse sampleEvent() {
        return new AuditEventResponse(EVENT_ID, AuditEventType.ROLE_ASSIGNED, "kc-admin-1", "admin", "kc-user-2",
                "jan", Map.of("roleName", "MANAGER"), Instant.parse("2026-11-10T09:00:00Z"), "corr-1");
    }

    // ---------------------------------------------------------------- GET /api/audit-events

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get(LIST_URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(auditEventService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "MANAGER", "SERVICE"})
    void listIsForbiddenForRolesWithoutAuditAccess(String role) throws Exception {
        mockMvc.perform(get(LIST_URL).with(as(role, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(auditEventService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "AUDITOR"})
    void adminAndAuditorCanListEvents(String role) throws Exception {
        when(auditEventService.list(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sampleEvent()), 0, 20, 1, 1));

        mockMvc.perform(get(LIST_URL).with(as(role, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.content[0].eventType").value("ROLE_ASSIGNED"))
                .andExpect(jsonPath("$.content[0].details.roleName").value("MANAGER"))
                .andExpect(jsonPath("$.content[0].occurredAt").value("2026-11-10T09:00:00Z"))
                .andExpect(jsonPath("$.content[0].correlationId").value("corr-1"));
    }

    @Test
    void listPassesFiltersToService() throws Exception {
        when(auditEventService.list(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(LIST_URL).with(as("AUDITOR"))
                        .param("eventType", "USER_DELETED")
                        .param("actorUserId", "kc-admin-1")
                        .param("from", "2026-11-01T00:00:00Z")
                        .param("to", "2026-11-11T00:00:00Z"))
                .andExpect(status().isOk());

        verify(auditEventService).list(eq(AuditEventType.USER_DELETED), eq("kc-admin-1"), isNull(),
                eq(Instant.parse("2026-11-01T00:00:00Z")), eq(Instant.parse("2026-11-11T00:00:00Z")),
                any(Pageable.class));
    }

    @Test
    void unknownEventTypeFilterIsBadRequest() throws Exception {
        mockMvc.perform(get(LIST_URL).with(as("ADMIN")).param("eventType", "PASSWORD_CHANGED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(auditEventService);
    }

    @Test
    void invalidDateFilterIsBadRequest() throws Exception {
        mockMvc.perform(get(LIST_URL).with(as("ADMIN")).param("from", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void tooLongActorFilterIsBadRequest() throws Exception {
        mockMvc.perform(get(LIST_URL).with(as("ADMIN")).param("actorUserId", "x".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---------------------------------------------------------------- GET /api/audit-events/{id}

    @Test
    void auditorCanReadEventDetails() throws Exception {
        when(auditEventService.get(EVENT_ID)).thenReturn(sampleEvent());

        mockMvc.perform(get(LIST_URL + "/" + EVENT_ID).with(as("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorUserId").value("kc-admin-1"))
                .andExpect(jsonPath("$.actorUsername").value("admin"))
                .andExpect(jsonPath("$.targetUserId").value("kc-user-2"))
                .andExpect(jsonPath("$.targetUsername").value("jan"));
    }

    @Test
    void missingEventIs404() throws Exception {
        when(auditEventService.get(EVENT_ID)).thenThrow(new AuditEventNotFoundException("Audit event not found"));

        mockMvc.perform(get(LIST_URL + "/" + EVENT_ID).with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_EVENT_NOT_FOUND"));
    }

    @Test
    void eventDetailsAreForbiddenForManager() throws Exception {
        mockMvc.perform(get(LIST_URL + "/" + EVENT_ID).with(as("MANAGER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditEventService);
    }

    @Test
    void auditEntriesCannotBeModifiedOrDeleted() throws Exception {
        // No update/delete endpoints exist (spec §6.3): the paths are not handled.
        mockMvc.perform(put(LIST_URL + "/" + EVENT_ID).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(LIST_URL + "/" + EVENT_ID).with(as("ADMIN")))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(auditEventService);
    }

    // ---------------------------------------------------------------- POST /internal/api/audit-events

    @Test
    void recordingRequestsAuthentication() throws Exception {
        mockMvc.perform(post(INTERNAL_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auditEventService);
    }

    @Test
    void userTokensCannotRecordEvents() throws Exception {
        mockMvc.perform(post(INTERNAL_URL).with(as("ADMIN", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditEventService);
    }

    @Test
    void serviceCanRecordEvent() throws Exception {
        when(auditEventService.record(any())).thenReturn(EVENT_ID);

        mockMvc.perform(post(INTERNAL_URL).with(as("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()));
    }

    @Test
    void eventWithoutRequiredFieldsIsRejected() throws Exception {
        mockMvc.perform(post(INTERNAL_URL).with(as("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"USER_CREATED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'actorUserId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'details')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'occurredAt')]").exists());
        verifyNoInteractions(auditEventService);
    }

    @Test
    void unknownEventTypeIsRejected() throws Exception {
        mockMvc.perform(post(INTERNAL_URL).with(as("SERVICE")).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT.replace("ROLE_ASSIGNED", "PASSWORD_CHANGED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(auditEventService);
    }

    @Test
    void actorIdWithIllegalCharactersIsRejected() throws Exception {
        mockMvc.perform(post(INTERNAL_URL).with(as("SERVICE")).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT.replace("kc-admin-1", "admin@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("actorUserId"));
        verifyNoInteractions(auditEventService);
    }

    @Test
    void tooLongUsernameIsRejected() throws Exception {
        mockMvc.perform(post(INTERNAL_URL).with(as("SERVICE")).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT.replace("\"jan\"", "\"" + "j".repeat(256) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetUsername"));
        verifyNoInteractions(auditEventService);
    }

    @Test
    void detailsWithTooManyEntriesAreRejected() throws Exception {
        StringBuilder details = new StringBuilder("{");
        for (int i = 0; i <= RecordAuditEventRequest.MAX_DETAIL_ENTRIES; i++) {
            details.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":1");
        }
        details.append("}");

        mockMvc.perform(post(INTERNAL_URL).with(as("SERVICE")).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT.replace("{\"roleName\":\"MANAGER\"}", details.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("details"));
        verifyNoInteractions(auditEventService);
    }

    @Test
    void oversizedDetailsAreBadRequest() throws Exception {
        when(auditEventService.record(any()))
                .thenThrow(new InvalidRequestException("'details' must not exceed 4096 bytes."));

        mockMvc.perform(post(INTERNAL_URL).with(as("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.detail").value("'details' must not exceed 4096 bytes."));
    }
}
