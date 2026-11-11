package pl.stojak.miniiam.audit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.stojak.miniiam.audit.domain.AuditEventType;

import java.time.Instant;
import java.util.Map;

/**
 * Event sent by a service to {@code POST /internal/api/audit-events} (spec §6.3). The correlation id is taken
 * from the {@code X-Correlation-Id} header, not from the body.
 *
 * @param actorUserId  keycloakId of the user who performed the operation
 * @param targetUserId keycloakId of the affected user (optional)
 * @param details      operation-specific data; never tokens, passwords or other credentials
 */
public record RecordAuditEventRequest(
        @NotNull AuditEventType eventType,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String actorUserId,
        @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String targetUserId,
        @NotNull @Size(max = MAX_DETAIL_ENTRIES) Map<String, Object> details,
        @NotNull Instant occurredAt) {

    public static final int MAX_DETAIL_ENTRIES = 20;
}
