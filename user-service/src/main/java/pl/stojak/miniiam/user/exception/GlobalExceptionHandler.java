package pl.stojak.miniiam.user.exception;

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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakBadRequestException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakConfigurationException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakConflictException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakNotFoundException;
import pl.stojak.miniiam.user.integration.keycloak.KeycloakUnavailableException;

import java.util.List;
import java.util.Map;

/**
 * Maps exceptions to RFC 9457 problem details (spec §15). Responses carry a stable {@code code}
 * ({@link ErrorCode}) and generic messages only: no stack traces, Keycloak messages or infrastructure details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleNotFound(UserNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found.");
    }

    @ExceptionHandler(KeycloakNotFoundException.class)
    ProblemDetail handleKeycloakNotFound(KeycloakNotFoundException e) {
        log.warn("Keycloak account missing: {}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User account not found.");
    }

    @ExceptionHandler(KeycloakConflictException.class)
    ProblemDetail handleConflict(KeycloakConflictException e) {
        return problem(HttpStatus.CONFLICT, ErrorCode.USERNAME_OR_EMAIL_TAKEN, "Username or e-mail is already in use.");
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail handleBusinessRule(BusinessRuleViolationException e) {
        return problem(HttpStatus.CONFLICT, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(OperationForbiddenException.class)
    ProblemDetail handleForbidden(OperationForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, e.getMessage());
    }

    @ExceptionHandler(KeycloakBadRequestException.class)
    ProblemDetail handleKeycloakBadRequest(KeycloakBadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_USER_DATA,
                "The identity provider rejected the user data (e.g. password policy).");
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

    @ExceptionHandler(UserSyncException.class)
    ProblemDetail handleSync(UserSyncException e) {
        // Details (ids, cause) are already logged where the inconsistency occurred.
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.USER_SYNC_FAILED,
                "The operation could not be completed consistently. Check the logs using the correlation id.");
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
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> Map.of(
                        "field", String.valueOf(result.getMethodParameter().getParameterName()),
                        "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage())))
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

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }
}
