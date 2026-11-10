package pl.stojak.miniiam.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Application profile of a user (spec §8.1). The account itself (credentials, roles) lives in Keycloak;
 * {@link #keycloakId} links both. The local {@link #id} is the API identifier and must not be confused with it.
 */
@Entity
@Table(name = "users")
public class UserProfile {

    @Id
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 64)
    private String keycloakId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: a stale profile cannot overwrite a newer one (null = new entity, no extra SELECT). */
    @Version
    private Long version;

    protected UserProfile() {
        // for JPA
    }

    public UserProfile(String keycloakId, String username, String email, String firstName, String lastName,
                       boolean enabled) {
        this.id = UUID.randomUUID();
        this.keycloakId = Objects.requireNonNull(keycloakId);
        this.username = Objects.requireNonNull(username);
        this.email = Objects.requireNonNull(email);
        this.firstName = firstName;
        this.lastName = lastName;
        this.enabled = enabled;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void updateDetails(String email, String firstName, String lastName) {
        this.email = Objects.requireNonNull(email);
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
