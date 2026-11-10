package pl.stojak.miniiam.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pl.stojak.miniiam.user.domain.UserProfile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByKeycloakId(String keycloakId);

    /**
     * Case-insensitive search by username, e-mail, first and last name.
     *
     * @param pattern lower-case LIKE pattern with {@code %}, {@code _} and {@code \} already escaped
     */
    @Query("""
            select u from UserProfile u
            where lower(u.username) like :pattern escape '\\'
               or lower(u.email) like :pattern escape '\\'
               or lower(coalesce(u.firstName, '')) like :pattern escape '\\'
               or lower(coalesce(u.lastName, '')) like :pattern escape '\\'
            """)
    Page<UserProfile> search(@Param("pattern") String pattern, Pageable pageable);

    /** Updates the status only (used when aligning with Keycloak), leaving other columns untouched. */
    @Transactional
    @Modifying
    @Query("""
            update UserProfile u
            set u.enabled = :enabled, u.updatedAt = :now, u.version = u.version + 1
            where u.id = :id""")
    int updateEnabled(@Param("id") UUID id, @Param("enabled") boolean enabled, @Param("now") Instant now);
}
