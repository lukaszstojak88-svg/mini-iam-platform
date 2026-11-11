package pl.stojak.miniiam.audit.mapper;

import pl.stojak.miniiam.audit.domain.AuditEvent;
import pl.stojak.miniiam.audit.dto.response.AuditEventResponse;

public final class AuditEventMapper {

    private AuditEventMapper() {
    }

    public static AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getEventType(), event.getActorUserId(),
                event.getTargetUserId(), event.getDetails(), event.getOccurredAt(), event.getCorrelationId());
    }
}
