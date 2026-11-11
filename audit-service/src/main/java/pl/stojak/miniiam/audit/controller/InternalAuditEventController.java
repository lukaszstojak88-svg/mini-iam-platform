package pl.stojak.miniiam.audit.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.stojak.miniiam.audit.dto.request.RecordAuditEventRequest;
import pl.stojak.miniiam.audit.dto.response.RecordedAuditEventResponse;
import pl.stojak.miniiam.audit.service.AuditEventService;

/**
 * Service-to-service endpoint for recording audit events (spec §6.3, §7). Callers authenticate with a
 * Client Credentials token carrying the SERVICE role; the rule is also enforced by {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/internal/api/audit-events")
public class InternalAuditEventController {

    private final AuditEventService auditEventService;

    public InternalAuditEventController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SERVICE')")
    public RecordedAuditEventResponse record(@Valid @RequestBody RecordAuditEventRequest request) {
        return new RecordedAuditEventResponse(auditEventService.record(request));
    }
}
