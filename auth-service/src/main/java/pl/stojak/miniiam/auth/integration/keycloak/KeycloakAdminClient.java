package pl.stojak.miniiam.auth.integration.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Minimal client of the Keycloak Admin REST API (spec §5.4) for reading and changing realm role mappings.
 * <p>
 * The {@link RestClient} passed in is already bound to {@code <keycloak>/admin/realms/<realm>} and authenticates
 * with a Client Credentials token of the technical client. Every HTTP or I/O failure is translated into a
 * {@link KeycloakIntegrationException} subtype. No retries. Request/response bodies are never logged.
 */
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private static final int ROLE_MEMBERS_PAGE_SIZE = 100;
    private static final ParameterizedTypeReference<List<KeycloakUser>> USER_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<KeycloakRole>> ROLE_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public KeycloakAdminClient(RestClient restClient) {
        this.restClient = restClient;
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

    /** All realm roles (including technical and built-in ones; filtering is up to the caller). */
    public List<KeycloakRole> listRealmRoles() {
        return nullToEmpty(call("listRealmRoles", () -> restClient.get()
                .uri("/roles?briefRepresentation=true")
                .retrieve()
                .body(ROLE_LIST)));
    }

    /** The realm role with this name, or empty when the realm has no such role. */
    public Optional<KeycloakRole> findRealmRole(String roleName) {
        try {
            return Optional.ofNullable(call("findRealmRole", () -> restClient.get()
                    .uri("/roles/{role}", roleName)
                    .retrieve()
                    .body(KeycloakRole.class)));
        } catch (KeycloakNotFoundException e) {
            return Optional.empty();
        }
    }

    /** Realm roles mapped directly to the user (without composite expansion). */
    public List<KeycloakRole> getDirectRealmRoles(String userId) {
        return nullToEmpty(call("getDirectRealmRoles", () -> restClient.get()
                .uri("/users/{id}/role-mappings/realm", userId)
                .retrieve()
                .body(ROLE_LIST)));
    }

    /** Effective realm roles of the user (including composite and default roles). */
    public List<KeycloakRole> getEffectiveRealmRoles(String userId) {
        return nullToEmpty(call("getEffectiveRealmRoles", () -> restClient.get()
                .uri("/users/{id}/role-mappings/realm/composite?briefRepresentation=true", userId)
                .retrieve()
                .body(ROLE_LIST)));
    }

    /** Maps the realm role directly to the user. Keycloak treats an existing mapping as a no-op. */
    public void addRealmRole(String userId, KeycloakRole role) {
        call("addRealmRole", () -> restClient.post()
                .uri("/users/{id}/role-mappings/realm", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(roleReference(role)))
                .retrieve()
                .toBodilessEntity());
    }

    /** Removes a direct mapping of the realm role from the user. */
    public void removeRealmRole(String userId, KeycloakRole role) {
        call("removeRealmRole", () -> restClient.method(HttpMethod.DELETE)
                .uri("/users/{id}/role-mappings/realm", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(roleReference(role)))
                .retrieve()
                .toBodilessEntity());
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

    private static Map<String, String> roleReference(KeycloakRole role) {
        return Map.of("id", role.id(), "name", role.name());
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private <T> T call(String operation, Supplier<T> request) {
        long start = System.nanoTime();
        try {
            T result = request.get();
            log.debug("Keycloak {} succeeded in {} ms", operation, elapsedMs(start));
            return result;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            log.atLevel(status.value() == HttpStatus.NOT_FOUND.value() ? Level.INFO : Level.WARN)
                    .addKeyValue("keycloak.operation", operation)
                    .addKeyValue("http.response.status_code", status.value())
                    .log("Keycloak {} failed with HTTP {} in {} ms", operation, status.value(), elapsedMs(start));
            throw translate(operation, status, e);
        } catch (ResourceAccessException e) {
            log.warn("Keycloak {} failed: {} after {} ms", operation, e.getClass().getSimpleName(), elapsedMs(start));
            throw new KeycloakUnavailableException(operation, e);
        } catch (OAuth2AuthorizationException e) {
            // Obtaining the Client Credentials token failed (wrong secret, Keycloak down or starting, ...).
            boolean ioProblem = hasCause(e, IOException.class) || hasCause(e, ResourceAccessException.class)
                    || hasCause(e, HttpServerErrorException.class);
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
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return new KeycloakNotFoundException(operation, e);
        }
        if (status.is4xxClientError()) {
            // Inputs are validated before calling Keycloak, so any other 4xx is a technical/configuration problem.
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
