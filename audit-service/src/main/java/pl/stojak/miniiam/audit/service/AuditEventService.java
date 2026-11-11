package pl.stojak.miniiam.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pl.stojak.miniiam.audit.config.CorrelationIdFilter;
import pl.stojak.miniiam.audit.domain.AuditEvent;
import pl.stojak.miniiam.audit.domain.AuditEventType;
import pl.stojak.miniiam.audit.dto.request.RecordAuditEventRequest;
import pl.stojak.miniiam.audit.dto.response.AuditEventResponse;
import pl.stojak.miniiam.audit.dto.response.PageResponse;
import pl.stojak.miniiam.audit.exception.AuditEventNotFoundException;
import pl.stojak.miniiam.audit.exception.InvalidRequestException;
import pl.stojak.miniiam.audit.mapper.AuditEventMapper;
import pl.stojak.miniiam.audit.repository.AuditEventRepository;
import pl.stojak.miniiam.audit.repository.AuditEventSpecifications;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Records audit events sent by other services and serves them read-only (spec §6.3). */
@Service
public class AuditEventService {

    static final Set<String> SORTABLE_PROPERTIES = Set.of("occurredAt", "eventType");
    /** Upper limit of {@code size}; larger values are rejected with 400, never silently clamped. */
    static final int MAX_PAGE_SIZE = 100;
    /** Limit of the serialized {@code details} object; events are small, this guards the table against abuse. */
    static final int MAX_DETAILS_BYTES = 4096;

    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditEventService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Stores the event together with the correlation id of the current request (already validated by
     * {@link CorrelationIdFilter}).
     */
    public UUID record(RecordAuditEventRequest request) {
        checkDetailsSize(request);
        AuditEvent saved = repository.save(new AuditEvent(request.eventType(), request.actorUserId(),
                blankToNull(request.actorUsername()), request.targetUserId(), blankToNull(request.targetUsername()),
                request.details(), request.occurredAt(), MDC.get(CorrelationIdFilter.MDC_KEY)));
        // Usernames and details are not logged: they are personal data.
        log.info("Audit event {} recorded: id={}, actor={}, target={}", saved.getEventType(), saved.getId(),
                saved.getActorUserId(), saved.getTargetUserId());
        return saved.getId();
    }

    public PageResponse<AuditEventResponse> list(AuditEventType eventType, String actorUserId, String targetUserId,
                                                 Instant from, Instant to, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("'size' must not exceed " + MAX_PAGE_SIZE + ".");
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Unsupported sort property: " + order.getProperty());
            }
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidRequestException("'from' must be earlier than 'to'.");
        }
        // Unique tie-breaker: many events share a type or a timestamp; without it paging is non-deterministic.
        Pageable stable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by("id")));
        Page<AuditEvent> page = repository.findAll(
                AuditEventSpecifications.matching(eventType, blankToNull(actorUserId), blankToNull(targetUserId),
                        from, to),
                stable);
        return PageResponse.of(page, AuditEventMapper::toResponse);
    }

    public AuditEventResponse get(UUID id) {
        return repository.findById(id)
                .map(AuditEventMapper::toResponse)
                .orElseThrow(() -> new AuditEventNotFoundException("Audit event not found"));
    }

    private void checkDetailsSize(RecordAuditEventRequest request) {
        try {
            int size = objectMapper.writeValueAsString(request.details()).getBytes(StandardCharsets.UTF_8).length;
            if (size > MAX_DETAILS_BYTES) {
                throw new InvalidRequestException("'details' must not exceed " + MAX_DETAILS_BYTES + " bytes.");
            }
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("'details' is not a valid JSON object.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
