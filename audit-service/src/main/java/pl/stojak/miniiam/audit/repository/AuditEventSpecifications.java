package pl.stojak.miniiam.audit.repository;

import org.springframework.data.jpa.domain.Specification;
import pl.stojak.miniiam.audit.domain.AuditEvent;
import pl.stojak.miniiam.audit.domain.AuditEventType;

import java.time.Instant;

/** Filters of the audit event listing (spec §6.3). A {@code null} value means "no filter". */
public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    public static Specification<AuditEvent> matching(AuditEventType eventType, String actorUserId,
                                                     String targetUserId, Instant from, Instant to) {
        return Specification.allOf(
                equalTo("eventType", eventType),
                equalTo("actorUserId", actorUserId),
                equalTo("targetUserId", targetUserId),
                occurredFrom(from),
                occurredBefore(to));
    }

    private static Specification<AuditEvent> equalTo(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    /** Inclusive lower bound. */
    private static Specification<AuditEvent> occurredFrom(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from);
    }

    /** Exclusive upper bound, so consecutive ranges (e.g. day by day) never count an event twice. */
    private static Specification<AuditEvent> occurredBefore(Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThan(root.<Instant>get("occurredAt"), to);
    }
}
