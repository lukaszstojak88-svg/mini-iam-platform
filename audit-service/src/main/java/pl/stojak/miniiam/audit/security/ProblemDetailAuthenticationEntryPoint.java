package pl.stojak.miniiam.audit.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Returns HTTP 401 as problem+json. The {@code WWW-Authenticate: Bearer} header is produced by the standard
 * {@link BearerTokenAuthenticationEntryPoint}; the body never reveals why the token was rejected.
 */
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAuthenticationEntryPoint.class);

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ProblemDetailResponseWriter writer;

    ProblemDetailAuthenticationEntryPoint(ProblemDetailResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthenticated request rejected: {} {} ({})",
                request.getMethod(), request.getRequestURI(), authException.getClass().getSimpleName());
        bearerEntryPoint.commence(request, response, authException);
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
    }
}
