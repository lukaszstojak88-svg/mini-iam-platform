package pl.stojak.miniiam.auth.integration.audit;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the Feign clients. Kept out of the application class so that sliced tests ({@code @WebMvcTest})
 * do not try to create them.
 */
@Configuration
@EnableFeignClients(clients = AuditClient.class)
class AuditFeignConfig {
}
