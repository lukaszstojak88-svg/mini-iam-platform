package pl.stojak.miniiam.auth.integration.audit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.stojak.miniiam.auth.config.CorrelationIdFilter;
import pl.stojak.miniiam.auth.integration.ServiceAccessTokenProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The real Feign client (Spring Cloud OpenFeign context, timeouts from configuration) against a local HTTP server
 * standing in for audit-service.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.keycloak-admin.client-secret=unused-in-this-test",
        "spring.cloud.openfeign.client.config.audit-service.read-timeout=500"
})
class AuditEventPublisherTest {

    private static final HttpServer SERVER = startServer();
    private static final List<RecordedRequest> REQUESTS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger RESPONSE_STATUS = new AtomicInteger(201);
    private static final AtomicLong RESPONSE_DELAY_MS = new AtomicLong(0);

    private static final AuditEventRequest EVENT = new AuditEventRequest("ROLE_ASSIGNED", "kc-admin", "kc-user",
            Map.of("roleName", "MANAGER"), Instant.parse("2026-11-10T09:00:00Z"));

    record RecordedRequest(String method, String path, String authorization, String correlationId, String body) {
    }

    @DynamicPropertySource
    static void auditServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.audit-service.url",
                () -> "http://localhost:" + SERVER.getAddress().getPort());
    }

    @Autowired
    private AuditEventPublisher publisher;

    @MockitoBean
    private ServiceAccessTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        REQUESTS.clear();
        RESPONSE_STATUS.set(201);
        RESPONSE_DELAY_MS.set(0);
        when(tokenProvider.accessToken()).thenReturn("test-service-token");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Test
    void sendsEventWithServiceTokenAndCorrelationIdOfCurrentRequest() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");

        publisher.publish(EVENT);

        assertThat(REQUESTS).hasSize(1);
        RecordedRequest request = REQUESTS.getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/internal/api/audit-events");
        assertThat(request.authorization()).isEqualTo("Bearer test-service-token");
        assertThat(request.correlationId()).isEqualTo("corr-123");
        assertThat(request.body())
                .contains("\"eventType\":\"ROLE_ASSIGNED\"")
                .contains("\"targetUserId\":\"kc-user\"")
                .contains("\"roleName\":\"MANAGER\"")
                .contains("\"occurredAt\":\"2026-11-10T09:00:00Z\"");
    }

    @Test
    void generatesCorrelationIdOutsideOfRequest() {
        publisher.publish(EVENT);

        assertThat(REQUESTS.getFirst().correlationId()).matches("[0-9a-f-]{36}");
    }

    @Test
    void serverErrorIsUnavailableAndNotRetried() {
        RESPONSE_STATUS.set(503);

        assertThatThrownBy(() -> publisher.publish(EVENT)).isInstanceOf(AuditUnavailableException.class);
        assertThat(REQUESTS).hasSize(1);
    }

    @Test
    void rejectedRequestIsIntegrationError() {
        RESPONSE_STATUS.set(403);

        assertThatThrownBy(() -> publisher.publish(EVENT)).isInstanceOf(AuditIntegrationException.class);
        // A rejected service token is dropped from the cache, so the next call obtains a fresh one.
        verify(tokenProvider).invalidate();
    }

    @Test
    void tokenEndpointServerErrorIsUnavailable() {
        when(tokenProvider.accessToken()).thenThrow(new OAuth2AuthorizationException(
                new OAuth2Error("invalid_token_response"), new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)));

        assertThatThrownBy(() -> publisher.publish(EVENT)).isInstanceOf(AuditUnavailableException.class);
    }

    @Test
    void readTimeoutIsUnavailableAndNotRetried() {
        RESPONSE_DELAY_MS.set(1500);

        assertThatThrownBy(() -> publisher.publish(EVENT)).isInstanceOf(AuditUnavailableException.class);
        assertThat(REQUESTS).hasSize(1);
    }

    @Test
    void missingServiceTokenStopsTheCall() {
        when(tokenProvider.accessToken())
                .thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_client")));

        assertThatThrownBy(() -> publisher.publish(EVENT)).isInstanceOf(AuditIntegrationException.class);
        assertThat(REQUESTS).isEmpty();
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                REQUESTS.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER_NAME), body));
                try {
                    Thread.sleep(RESPONSE_DELAY_MS.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(RESPONSE_STATUS.get(), -1);
                exchange.close();
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
