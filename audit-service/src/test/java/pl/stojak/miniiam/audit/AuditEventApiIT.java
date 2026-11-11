package pl.stojak.miniiam.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recording and reading audit events against a real PostgreSQL (JSONB, filters, paging, correlation id). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditEventApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("init-schema.sql");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static RequestPostProcessor as(String... roles) {
        return jwt().jwt(token -> token.subject("kc-caller"))
                .authorities(Arrays.stream(roles)
                        .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM audit.audit_events");
    }

    private String record(String eventType, String actor, String target, String details, String occurredAt,
                          String correlationId) throws Exception {
        String body = """
                {"eventType":"%s","actorUserId":"%s","targetUserId":%s,"details":%s,"occurredAt":"%s"}"""
                .formatted(eventType, actor, target == null ? "null" : "\"" + target + "\"", details, occurredAt);
        String response = mockMvc.perform(post("/internal/api/audit-events").with(as("SERVICE"))
                        .header("X-Correlation-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void recordedEventIsStoredWithCorrelationIdAndReadableByAuditor() throws Exception {
        String id = record("ROLE_ASSIGNED", "kc-admin", "kc-user", "{\"roleName\":\"MANAGER\"}",
                "2026-11-10T09:00:00Z", "it-corr-1");

        mockMvc.perform(get("/api/audit-events/" + id).with(as("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("ROLE_ASSIGNED"))
                .andExpect(jsonPath("$.actorUserId").value("kc-admin"))
                .andExpect(jsonPath("$.targetUserId").value("kc-user"))
                .andExpect(jsonPath("$.details.roleName").value("MANAGER"))
                .andExpect(jsonPath("$.occurredAt").value("2026-11-10T09:00:00Z"))
                .andExpect(jsonPath("$.correlationId").value("it-corr-1"));

        // details is real JSONB, not text.
        String detailsType = jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(details) FROM audit.audit_events WHERE id = ?::uuid", String.class, id);
        assertThat(detailsType).isEqualTo("object");
    }

    @Test
    void invalidCorrelationIdIsReplacedByGeneratedOne() throws Exception {
        String id = record("USER_CREATED", "kc-admin", "kc-new", "{\"username\":\"jan\"}",
                "2026-11-10T09:00:00Z", "not valid!");

        String stored = jdbcTemplate.queryForObject(
                "SELECT correlation_id FROM audit.audit_events WHERE id = ?::uuid", String.class, id);
        assertThat(stored).isNotEqualTo("not valid!").matches("[0-9a-f-]{36}");
    }

    @Test
    void listFiltersByTypeActorTargetAndDateRange() throws Exception {
        record("USER_CREATED", "kc-admin", "kc-a", "{\"username\":\"a\"}", "2026-11-01T10:00:00Z", "c1");
        record("USER_DELETED", "kc-admin", "kc-a", "{\"username\":\"a\"}", "2026-11-05T10:00:00Z", "c2");
        record("USER_CREATED", "kc-manager", "kc-b", "{\"username\":\"b\"}", "2026-11-09T10:00:00Z", "c3");
        record("ROLE_ASSIGNED", "kc-admin", "kc-b", "{\"roleName\":\"AUDITOR\"}", "2026-11-10T10:00:00Z", "c4");

        // Default order: newest first.
        mockMvc.perform(get("/api/audit-events").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].correlationId").value("c4"))
                .andExpect(jsonPath("$.content[3].correlationId").value("c1"));

        mockMvc.perform(get("/api/audit-events").with(as("ADMIN")).param("eventType", "USER_CREATED"))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/audit-events").with(as("ADMIN")).param("actorUserId", "kc-admin")
                        .param("targetUserId", "kc-a"))
                .andExpect(jsonPath("$.totalElements").value(2));

        // [from, to): the event at exactly "to" is excluded.
        mockMvc.perform(get("/api/audit-events").with(as("ADMIN"))
                        .param("from", "2026-11-05T10:00:00Z").param("to", "2026-11-10T10:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].correlationId").value("c3"))
                .andExpect(jsonPath("$.content[1].correlationId").value("c2"));
    }

    @Test
    void listIsPagedAndSortable() throws Exception {
        for (int day = 1; day <= 5; day++) {
            record("USER_UPDATED", "kc-admin", "kc-x", "{\"changedFields\":[\"email\"]}",
                    "2026-11-0" + day + "T08:00:00Z", "p" + day);
        }

        JsonNode secondPage = objectMapper.readTree(mockMvc.perform(get("/api/audit-events").with(as("ADMIN"))
                        .param("page", "1").param("size", "2").param("sort", "occurredAt,asc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(secondPage.get("totalElements").asInt()).isEqualTo(5);
        assertThat(secondPage.get("totalPages").asInt()).isEqualTo(3);
        assertThat(secondPage.get("content")).extracting(node -> node.get("correlationId").asText())
                .containsExactly("p3", "p4");
    }

    @Test
    void pageSizeAboveLimitIsRejectedNotClamped() throws Exception {
        mockMvc.perform(get("/api/audit-events").with(as("ADMIN")).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.detail").value("'size' must not exceed 100."));
        mockMvc.perform(get("/api/audit-events").with(as("ADMIN")).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void unknownEventIs404() throws Exception {
        mockMvc.perform(get("/api/audit-events/00000000-0000-0000-0000-000000000000").with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_EVENT_NOT_FOUND"));
    }
}
