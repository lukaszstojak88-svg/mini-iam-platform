package pl.stojak.miniiam.user.integration.audit;

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
 * Records audit events in audit-service (spec §6.3, §7) - synchronously, after the business operation succeeded.
 * <p>
 * Best effort (project decision, spec §7): a failure to record the event never fails the operation that has
 * already been applied. It is logged at ERROR level with the event type, actor, target and correlation id (MDC)
 * so the missing entry can be reconciled manually. Event details are never logged (they may contain personal data).
 */
@Component
public class AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

    private final AuditClient auditClient;

    AuditEventPublisher(AuditClient auditClient) {
        this.auditClient = auditClient;
    }

    /**
     * @return {@code true} when audit-service stored the event, {@code false} when recording failed (already logged)
     */
    public boolean publish(AuditEventRequest event) {
        long start = System.nanoTime();
        try {
            send(event);
            log.info("Audit event {} recorded in {} ms", event.eventType(), elapsedMs(start));
            return true;
        } catch (AuditUnavailableException | AuditIntegrationException e) {
            log.error("Audit event NOT recorded (operation already applied): type={}, actor={}, target={}, "
                            + "after {} ms: {}", event.eventType(), event.actorUserId(), event.targetUserId(),
                    elapsedMs(start), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Audit event NOT recorded (operation already applied): type={}, actor={}, target={}, "
                            + "unexpected {}", event.eventType(), event.actorUserId(), event.targetUserId(),
                    e.getClass().getSimpleName());
        }
        return false;
    }

    /** Calls audit-service and translates every failure into {@link AuditUnavailableException} or {@link AuditIntegrationException}. */
    private void send(AuditEventRequest event) {
        try {
            auditClient.record(event);
        } catch (AuditUnavailableException | AuditIntegrationException e) {
            throw e;
        } catch (RetryableException e) {
            // I/O problem: connection refused, connect/read timeout.
            throw new AuditUnavailableException("audit-service unreachable (" + rootCauseName(e) + ")", e);
        } catch (OAuth2AuthorizationException e) {
            // I/O or 5xx from the token endpoint = Keycloak unavailable; anything else = credentials rejected.
            boolean ioProblem = hasCause(e, IOException.class) || hasCause(e, ResourceAccessException.class)
                    || hasCause(e, HttpServerErrorException.class);
            String message = "obtaining service token failed (" + (ioProblem ? "I/O" : "authorization")
                    + "), error code " + e.getError().getErrorCode();
            throw ioProblem ? new AuditUnavailableException(message, e) : new AuditIntegrationException(message, e);
        } catch (FeignException e) {
            throw new AuditUnavailableException("audit-service call failed (" + e.getClass().getSimpleName() + ")", e);
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
