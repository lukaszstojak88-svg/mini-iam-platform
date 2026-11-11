package pl.stojak.miniiam.auth.integration.audit;

import feign.Response;
import feign.codec.ErrorDecoder;

/**
 * Maps HTTP errors of audit-service to controlled exceptions (spec §7.2). Response bodies are not read or logged.
 */
class AuditErrorDecoder implements ErrorDecoder {

    private final Runnable invalidateServiceToken;

    AuditErrorDecoder(Runnable invalidateServiceToken) {
        this.invalidateServiceToken = invalidateServiceToken;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == 401 || status == 403) {
            // The cached service token was rejected - do not reuse it for the next call.
            invalidateServiceToken.run();
        }
        if (status >= 500 || status == 429) {
            return new AuditUnavailableException("audit-service responded with HTTP " + status, null);
        }
        // 400 (rejected event), 401/403 (service token not accepted) - a defect or configuration problem.
        return new AuditIntegrationException("audit-service rejected the request with HTTP " + status, null);
    }
}
