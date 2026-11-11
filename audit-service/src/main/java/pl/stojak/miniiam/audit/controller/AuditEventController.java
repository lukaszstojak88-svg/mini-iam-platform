package pl.stojak.miniiam.audit.controller;

import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.stojak.miniiam.audit.domain.AuditEventType;
import pl.stojak.miniiam.audit.dto.response.AuditEventResponse;
import pl.stojak.miniiam.audit.dto.response.PageResponse;
import pl.stojak.miniiam.audit.service.AuditEventService;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only audit API (spec §6.3) for ADMIN and AUDITOR. There are deliberately no update or delete endpoints:
 * audit entries are immutable through the public API.
 */
@RestController
@RequestMapping("/api/audit-events")
public class AuditEventController {

    private final AuditEventService auditEventService;

    public AuditEventController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    /**
     * @param from inclusive lower bound of {@code occurredAt} (ISO-8601, e.g. {@code 2026-11-10T00:00:00Z})
     * @param to   exclusive upper bound of {@code occurredAt}
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public PageResponse<AuditEventResponse> list(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) @Size(max = 64) String actorUserId,
            @RequestParam(required = false) @Size(max = 64) String targetUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditEventService.list(eventType, actorUserId, targetUserId, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AuditEventResponse get(@PathVariable UUID id) {
        return auditEventService.get(id);
    }
}
