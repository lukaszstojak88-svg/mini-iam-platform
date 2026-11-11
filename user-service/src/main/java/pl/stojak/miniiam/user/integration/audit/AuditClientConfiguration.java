package pl.stojak.miniiam.user.integration.audit;

import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import pl.stojak.miniiam.user.config.CorrelationIdRequestInterceptor;
import pl.stojak.miniiam.user.integration.ServiceAccessTokenProvider;

/**
 * Configuration of the {@link AuditClient} Feign context. Deliberately not a {@code @Configuration}: its beans must
 * not leak into the application context or other Feign clients.
 */
class AuditClientConfiguration {

    /** Service-to-service authentication (spec §7.5): Client Credentials token with the SERVICE role. */
    @Bean
    RequestInterceptor serviceTokenRequestInterceptor(ServiceAccessTokenProvider tokenProvider) {
        return template -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.accessToken());
    }

    /** Propagates the correlation id of the current request (spec §16.2). */
    @Bean
    RequestInterceptor correlationIdRequestInterceptor() {
        return new CorrelationIdRequestInterceptor();
    }

    @Bean
    ErrorDecoder auditErrorDecoder(ServiceAccessTokenProvider tokenProvider) {
        return new AuditErrorDecoder(tokenProvider::invalidate);
    }

    /** Recording an event is not idempotent - no retries (spec §7.3-7.4). */
    @Bean
    Retryer auditRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
