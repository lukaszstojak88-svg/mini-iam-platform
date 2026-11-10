package pl.stojak.miniiam.user.integration.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Minimal client of the Keycloak Admin REST API (spec §5.4) for the realm configured in {@link KeycloakAdminProperties}.
 * <p>
 * The {@link RestClient} passed in is already bound to {@code <keycloak>/admin/realms/<realm>} and authenticates
 * with a Client Credentials token of the technical client. Every HTTP or I/O failure is translated into a
 * {@link KeycloakIntegrationException} subtype. No retries: write operations are not idempotent.
 * Request/response bodies are never logged (they may contain passwords or personal data).
 */
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private static final int ROLE_MEMBERS_PAGE_SIZE = 100;
    private static final ParameterizedTypeReference<List<KeycloakUser>> USER_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> ROLE_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public KeycloakAdminClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Creates an enabled account with a temporary password (Keycloak forces a change at first login). Returns its id. */
    public String createUser(NewKeycloakUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", user.username());
        body.put("email", user.email());
        body.put("firstName", user.firstName());
        body.put("lastName", user.lastName());
        body.put("enabled", true);
        body.put("emailVerified", false);
        body.put("credentials", List.of(Map.of(
                "type", "password",
                "value", user.temporaryPassword(),
                "temporary", true)));

        ResponseEntity<Void> response = call("createUser", () -> restClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
        URI location = response.getHeaders().getLocation();
        if (location == null) {
            throw new KeycloakConfigurationException("createUser", new IllegalStateException("Missing Location header"));
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Returns the account or empty when it does not exist. */
    public Optional<KeycloakUser> findUser(String userId) {
        try {
            return Optional.ofNullable(call("findUser", () -> restClient.get()
                    .uri("/users/{id}", userId)
                    .retrieve()
                    .body(KeycloakUser.class)));
        } catch (KeycloakNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Updates e-mail and names with a partial representation: Keycloak keeps fields that are not sent
     * (e.g. {@code enabled}), so a concurrent status change is not overwritten.
     *
     * @param emailChanged when {@code true}, {@code emailVerified} is reset - a new address is not verified
     */
    public void updateProfile(String userId, String email, String firstName, String lastName, boolean emailChanged) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("email", email);
        // Keycloak ignores null values on update; an empty string clears the attribute.
        representation.put("firstName", firstName == null ? "" : firstName);
        representation.put("lastName", lastName == null ? "" : lastName);
        if (emailChanged) {
            representation.put("emailVerified", false);
        }
        putRepresentation(userId, representation, "updateProfile");
    }

    /** Changes only the {@code enabled} flag (partial representation). */
    public void setEnabled(String userId, boolean enabled) {
        putRepresentation(userId, Map.of("enabled", enabled), "setEnabled");
    }

    /** Id of the account with exactly this username, if any. */
    public Optional<String> findUserIdByUsername(String username) {
        List<KeycloakUser> users = call("findUserIdByUsername", () -> restClient.get()
                .uri("/users?username={username}&exact=true&briefRepresentation=true", username)
                .retrieve()
                .body(USER_LIST));
        return users == null ? Optional.empty()
                : users.stream().filter(u -> username.equals(u.username())).map(KeycloakUser::id).findFirst();
    }

    /** Deletes the account. Returns {@code false} when it did not exist (idempotent delete). */
    public boolean deleteUser(String userId) {
        try {
            call("deleteUser", () -> restClient.delete()
                    .uri("/users/{id}", userId)
                    .retrieve()
                    .toBodilessEntity());
            return true;
        } catch (KeycloakNotFoundException e) {
            return false;
        }
    }

    /** Direct members of a realm role (all pages). */
    public List<KeycloakUser> getRealmRoleMembers(String roleName) {
        List<KeycloakUser> members = new ArrayList<>();
        int first = 0;
        while (true) {
            int offset = first;
            List<KeycloakUser> page = call("getRealmRoleMembers", () -> restClient.get()
                    .uri("/roles/{role}/users?first={first}&max={max}&briefRepresentation=true",
                            roleName, offset, ROLE_MEMBERS_PAGE_SIZE)
                    .retrieve()
                    .body(USER_LIST));
            if (page == null || page.isEmpty()) {
                return members;
            }
            members.addAll(page);
            if (page.size() < ROLE_MEMBERS_PAGE_SIZE) {
                return members;
            }
            first += ROLE_MEMBERS_PAGE_SIZE;
        }
    }

    /** Effective realm role names of a user (including composite and default roles). */
    public List<String> getEffectiveRealmRoles(String userId) {
        List<Map<String, Object>> roles = call("getEffectiveRealmRoles", () -> restClient.get()
                .uri("/users/{id}/role-mappings/realm/composite", userId)
                .retrieve()
                .body(ROLE_LIST));
        if (roles == null) {
            return List.of();
        }
        return roles.stream().map(role -> String.valueOf(role.get("name"))).toList();
    }

    private void putRepresentation(String userId, Map<String, Object> representation, String operation) {
        call(operation, () -> restClient.put()
                .uri("/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(representation)
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(String operation, Supplier<T> request) {
        long start = System.nanoTime();
        try {
            T result = request.get();
            log.debug("Keycloak {} succeeded in {} ms", operation, elapsedMs(start));
            return result;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            log.atLevel(status.value() == 404 || status.value() == 409 ? org.slf4j.event.Level.INFO : org.slf4j.event.Level.WARN)
                    .addKeyValue("keycloak.operation", operation)
                    .addKeyValue("http.response.status_code", status.value())
                    .log("Keycloak {} failed with HTTP {} in {} ms", operation, status.value(), elapsedMs(start));
            throw translate(operation, status, e);
        } catch (ResourceAccessException e) {
            log.warn("Keycloak {} failed: {} after {} ms", operation, e.getClass().getSimpleName(), elapsedMs(start));
            throw new KeycloakUnavailableException(operation, e);
        } catch (OAuth2AuthorizationException e) {
            // Obtaining the Client Credentials token failed (wrong secret, Keycloak down, ...).
            boolean ioProblem = hasCause(e, IOException.class) || hasCause(e, ResourceAccessException.class);
            log.warn("Keycloak {}: obtaining access token failed ({}), error code {}",
                    operation, ioProblem ? "I/O" : "authorization", e.getError().getErrorCode());
            throw ioProblem ? new KeycloakUnavailableException(operation, e)
                    : new KeycloakConfigurationException(operation, e);
        } catch (RestClientException e) {
            log.warn("Keycloak {} failed: {}", operation, e.getClass().getSimpleName());
            throw new KeycloakUnavailableException(operation, e);
        }
    }

    private static KeycloakIntegrationException translate(String operation, HttpStatusCode status, Exception e) {
        if (status.value() == HttpStatus.CONFLICT.value()) {
            return new KeycloakConflictException(operation, e);
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return new KeycloakNotFoundException(operation, e);
        }
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return new KeycloakBadRequestException(operation, e);
        }
        if (status.value() == HttpStatus.UNAUTHORIZED.value() || status.value() == HttpStatus.FORBIDDEN.value()) {
            return new KeycloakConfigurationException(operation, e);
        }
        return new KeycloakUnavailableException(operation, e);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
