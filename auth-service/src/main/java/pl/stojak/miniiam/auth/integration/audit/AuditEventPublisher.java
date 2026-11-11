package pl.stojak.miniiam.auth.integration.audit;

import feign.FeignException;
import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

/**
 * Records audit events in audit-service (spec §6.3, §7). Synchronous: when audit-service is unavailable the
 * caller gets {@link AuditUnavailableException} and the business operation fails visibly (documented MVP
 * behaviour, spec §7). Every failure is translated into {@link AuditUnavailableException} or
 * {@link AuditIntegrationException}; event details are never logged.
 */
@Component
public class AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

    private final AuditClient auditClient;

    AuditEventPublisher(AuditClient auditClient) {
        this.auditClient = auditClient;
    }

    public void publish(AuditEventRequest event) {
        long start = System.nanoTime();
        try {
            auditClient.record(event);
            log.info("Audit event {} recorded in {} ms", event.eventType(), elapsedMs(start));
        } catch (AuditUnavailableException | AuditIntegrationException e) {
            log.warn("Recording audit event {} failed after {} ms: {}", event.eventType(), elapsedMs(start), e.getMessage());
            throw e;
        } catch (RetryableException e) {
            // I/O problem: connection refused, connect/read timeout.
            log.warn("Recording audit event {} failed after {} ms: audit-service unreachable ({})",
                    event.eventType(), elapsedMs(start), rootCauseName(e));
            throw new AuditUnavailableException("audit-service is unreachable", e);
        } catch (OAuth2AuthorizationException e) {
            // I/O or 5xx from the token endpoint = Keycloak unavailable; anything else = credentials rejected.
            boolean ioProblem = hasCause(e, IOException.class) || hasCause(e, ResourceAccessException.class)
                    || hasCause(e, HttpServerErrorException.class);
            log.warn("Recording audit event {} failed: obtaining service token failed ({}), error code {}",
                    event.eventType(), ioProblem ? "I/O" : "authorization", e.getError().getErrorCode());
            throw ioProblem ? new AuditUnavailableException("Service token unavailable", e)
                    : new AuditIntegrationException("Service token rejected", e);
        } catch (FeignException e) {
            log.warn("Recording audit event {} failed after {} ms: {}", event.eventType(), elapsedMs(start),
                    e.getClass().getSimpleName());
            throw new AuditUnavailableException("audit-service call failed", e);
        }
    }

    private static String rootCauseName(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
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
