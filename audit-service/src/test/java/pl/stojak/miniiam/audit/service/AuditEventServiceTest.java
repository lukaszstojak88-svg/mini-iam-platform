package pl.stojak.miniiam.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import pl.stojak.miniiam.audit.config.CorrelationIdFilter;
import pl.stojak.miniiam.audit.domain.AuditEvent;
import pl.stojak.miniiam.audit.domain.AuditEventType;
import pl.stojak.miniiam.audit.dto.request.RecordAuditEventRequest;
import pl.stojak.miniiam.audit.exception.AuditEventNotFoundException;
import pl.stojak.miniiam.audit.exception.InvalidRequestException;
import pl.stojak.miniiam.audit.repository.AuditEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditEventServiceTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-10T09:00:00Z");

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditEventService service =
            new AuditEventService(repository, new ObjectMapper().registerModule(new JavaTimeModule()));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static RecordAuditEventRequest request(Map<String, Object> details) {
        return new RecordAuditEventRequest(AuditEventType.USER_UPDATED, "kc-admin", "kc-user", details, OCCURRED_AT);
    }

    @Test
    void recordStoresEventWithCorrelationIdOfCurrentRequest() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID id = service.record(request(Map.of("changedFields", List.of("email"))));

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(saved.capture());
        AuditEvent event = saved.getValue();
        assertThat(id).isEqualTo(event.getId());
        assertThat(event.getEventType()).isEqualTo(AuditEventType.USER_UPDATED);
        assertThat(event.getActorUserId()).isEqualTo("kc-admin");
        assertThat(event.getTargetUserId()).isEqualTo("kc-user");
        assertThat(event.getDetails()).containsEntry("changedFields", List.of("email"));
        assertThat(event.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.getCorrelationId()).isEqualTo("corr-123");
        assertThat(event.isNew()).isTrue();
    }

    @Test
    void recordRejectsOversizedDetails() {
        Map<String, Object> details = Map.of("note", "x".repeat(AuditEventService.MAX_DETAILS_BYTES));

        assertThatThrownBy(() -> service.record(request(details)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("4096");
        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAddsIdTieBreakerToRequestedSort() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, null, null, PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "occurredAt")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort())
                .containsExactly(Sort.Order.desc("occurredAt"), Sort.Order.asc("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMapsEntitiesToResponses() {
        AuditEvent event = new AuditEvent(AuditEventType.ROLE_REMOVED, "kc-admin", "kc-user",
                Map.of("roleName", "AUDITOR"), OCCURRED_AT, "corr-9");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1));

        var page = service.list(AuditEventType.ROLE_REMOVED, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(event.getId());
            assertThat(response.details()).containsEntry("roleName", "AUDITOR");
            assertThat(response.correlationId()).isEqualTo("corr-9");
        });
    }

    @Test
    void listRejectsPageSizeAboveLimit() {
        assertThatThrownBy(() -> service.list(null, null, null, null, null, PageRequest.of(0, 101)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("'size' must not exceed 100.");
        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAcceptsPageSizeAtLimit() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, null, null, PageRequest.of(0, 100));

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listRejectsSortOutsideWhitelist() {
        assertThatThrownBy(() -> service.list(null, null, null, null, null,
                PageRequest.of(0, 20, Sort.by("details"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("details");
        verifyNoInteractions(repository);
    }

    @Test
    void listRejectsEmptyOrReversedDateRange() {
        Instant now = Instant.parse("2026-11-10T00:00:00Z");

        assertThatThrownBy(() -> service.list(null, null, null, now, now, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.list(null, null, null, now.plusSeconds(1), now, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void getThrowsWhenEventDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(AuditEventNotFoundException.class);
    }
}
