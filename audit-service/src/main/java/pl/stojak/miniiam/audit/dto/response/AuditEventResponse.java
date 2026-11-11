package pl.stojak.miniiam.audit.dto.response;

import pl.stojak.miniiam.audit.domain.AuditEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        AuditEventType eventType,
        String actorUserId,
        String actorUsername,
        String targetUserId,
        String targetUsername,
        Map<String, Object> details,
        Instant occurredAt,
        String correlationId) {
}
