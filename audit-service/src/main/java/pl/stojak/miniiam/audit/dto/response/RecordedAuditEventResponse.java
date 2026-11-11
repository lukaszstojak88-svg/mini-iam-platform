package pl.stojak.miniiam.audit.dto.response;

import java.util.UUID;

/** Response of {@code POST /internal/api/audit-events}: id of the stored event. */
public record RecordedAuditEventResponse(UUID id) {
}
