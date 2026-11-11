package pl.stojak.miniiam.auth.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakConfigurationException;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakNotFoundException;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakUnavailableException;

import java.util.List;
import java.util.Map;

/**
 * Maps exceptions to RFC 9457 problem details (spec §15). Responses carry a stable {@code code}
 * ({@link ErrorCode}) and generic messages only: no stack traces, Keycloak messages or infrastructure details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException e) {
        return problem(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(KeycloakNotFoundException.class)
    ProblemDetail handleKeycloakNotFound(KeycloakNotFoundException e) {
        // Checked before every change, so this means the user or role disappeared concurrently.
        log.warn("Keycloak resource missing: {}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "User or role not found.");
    }

    @ExceptionHandler(KeycloakUnavailableException.class)
    ProblemDetail handleKeycloakUnavailable(KeycloakUnavailableException e) {
        log.error("Keycloak unavailable: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.KEYCLOAK_UNAVAILABLE,
                "The identity provider is temporarily unavailable. Try again later.");
    }

    @ExceptionHandler(KeycloakConfigurationException.class)
    ProblemDetail handleKeycloakConfiguration(KeycloakConfigurationException e) {
        log.error("Keycloak integration error: {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, ErrorCode.KEYCLOAK_INTEGRATION_ERROR,
                "The identity provider rejected the request.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            // Let Spring Security produce the standard 401/403 responses.
            throw e;
        }
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Unexpected error.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed.");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed.");
        // Constraints on path variables trigger method validation, which then also validates the @Valid body;
        // body errors are reported per field (e.g. "roleName"), the others per parameter (e.g. "userId").
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result instanceof ParameterErrors parameterErrors
                        ? parameterErrors.getFieldErrors().stream().map(error -> fieldError(
                                error.getField(), error.getDefaultMessage()))
                        : result.getResolvableErrors().stream().map(error -> fieldError(
                                String.valueOf(result.getMethodParameter().getParameterName()),
                                error.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        // Framework-generated problems (malformed JSON, wrong types, ...) get a code as well.
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            problem.setProperty("code", statusCode.is4xxClientError()
                    ? ErrorCode.INVALID_REQUEST.name() : ErrorCode.INTERNAL_ERROR.name());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static Map<String, String> fieldError(String field, @Nullable String message) {
        return Map.of("field", field, "message", message == null ? "invalid" : message);
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }
}
