package pl.stojak.miniiam.user.integration.audit;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client of audit-service's internal API. The base URL and timeouts come from
 * {@code spring.cloud.openfeign.client.config.audit-service.*}; authentication, correlation id, error mapping
 * and the no-retry policy from {@link AuditClientConfiguration}. Use it through {@link AuditEventPublisher}.
 */
@FeignClient(name = "audit-service", configuration = AuditClientConfiguration.class)
interface AuditClient {

    @PostMapping("/internal/api/audit-events")
    void record(@RequestBody AuditEventRequest event);
}
