package pl.stojak.miniiam.auth.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Adds the correlation id of the current request ({@link CorrelationIdFilter}) to every outgoing Feign call
 * (spec §16.2). Outside of an HTTP request (no id in the MDC) a new id is generated, so the downstream service
 * still receives one.
 */
public class CorrelationIdRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        template.removeHeader(CorrelationIdFilter.HEADER_NAME);
        template.header(CorrelationIdFilter.HEADER_NAME,
                CorrelationIdFilter.resolveCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
