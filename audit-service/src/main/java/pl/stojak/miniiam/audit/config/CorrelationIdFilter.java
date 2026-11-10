package pl.stojak.miniiam.audit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns a correlation ID to every request (spec §16.2): accepts a valid
 * {@code X-Correlation-Id} header or generates a UUID, puts it into the MDC and the
 * response header, and logs request completion.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");
    private static final String ACTUATOR_PATH_PREFIX = "/actuator";
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            logRequestCompletion(request, response, startNanos);
            MDC.remove(MDC_KEY);
        }
    }

    static String resolveCorrelationId(String headerValue) {
        if (headerValue != null && VALID_ID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }

    private void logRequestCompletion(HttpServletRequest request, HttpServletResponse response, long startNanos) {
        String path = request.getRequestURI();
        if (path.startsWith(ACTUATOR_PATH_PREFIX)) {
            return;
        }
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.atInfo()
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.path", path)
                .addKeyValue("http.response.status_code", response.getStatus())
                .addKeyValue("event.duration_ms", durationMs)
                .log("HTTP {} {} -> {} ({} ms)", request.getMethod(), path, response.getStatus(), durationMs);
    }
}
