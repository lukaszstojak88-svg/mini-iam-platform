package pl.stojak.miniiam.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Returns HTTP 403 as problem+json and logs the denied attempt (subject id only, no token). */
class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

    private final ProblemDetailResponseWriter writer;

    ProblemDetailAccessDeniedHandler(ProblemDetailResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String subject = authentication != null ? authentication.getName() : "anonymous";
        log.warn("Access denied: {} {} for subject {}", request.getMethod(), request.getRequestURI(), subject);
        writer.write(request, response, HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
    }
}
