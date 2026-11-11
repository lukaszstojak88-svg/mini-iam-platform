package pl.stojak.miniiam.auth.integration.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Audit event sent to audit-service (spec §6.3). The correlation id travels in the {@code X-Correlation-Id}
 * header, not in the body. {@code details} must never contain tokens, passwords or other credentials.
 *
 * @param actorUserId  keycloakId of the user who performed the operation
 * @param targetUserId keycloakId of the affected user (nullable)
 */
public record AuditEventRequest(
        String eventType,
        String actorUserId,
        String targetUserId,
        Map<String, Object> details,
        Instant occurredAt) {
}
