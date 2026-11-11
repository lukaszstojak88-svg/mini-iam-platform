package pl.stojak.miniiam.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A recorded audit event (spec §8.2). Immutable: created once, never updated (Hibernate ignores changes to
 * {@link Immutable} entities and there are no setters).
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private AuditEventType eventType;

    @Column(name = "actor_user_id", nullable = false, length = 64)
    private String actorUserId;

    @Column(name = "target_user_id", length = 64)
    private String targetUserId;

    /** Username of the actor at the time of the event; null for events recorded before it was stored. */
    @Column(name = "actor_username")
    private String actorUsername;

    /** Username of the target user at the time of the event; null when unknown or there is no target. */
    @Column(name = "target_username")
    private String targetUsername;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private Map<String, Object> details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /** The id is assigned in Java, so Spring Data must be told the entity is new (persist, not merge + SELECT). */
    @Transient
    private boolean isNew = true;

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(AuditEventType eventType, String actorUserId, String actorUsername, String targetUserId,
                      String targetUsername, Map<String, Object> details, Instant occurredAt,
                      String correlationId) {
        this.id = UUID.randomUUID();
        this.eventType = Objects.requireNonNull(eventType);
        this.actorUserId = Objects.requireNonNull(actorUserId);
        this.actorUsername = actorUsername;
        this.targetUserId = targetUserId;
        this.targetUsername = targetUsername;
        // LinkedHashMap: keeps key order and allows JSON null values (Map.copyOf would reject them).
        this.details = new LinkedHashMap<>(Objects.requireNonNull(details));
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.correlationId = correlationId;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
