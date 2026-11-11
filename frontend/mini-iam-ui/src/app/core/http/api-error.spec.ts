import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { isAccessDenied, toApiError } from './api-error';

function httpError(status: number, body: unknown = null, headers: Record<string, string> = {}): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body, headers: new HttpHeaders(headers) });
}

describe('toApiError', () => {
  it('uses the UI message for a known code, never the backend detail', () => {
    const error = toApiError(httpError(409, { code: 'LAST_ADMIN', detail: 'Internal backend text' }));

    expect(error.code).toBe('LAST_ADMIN');
    expect(error.message).toContain('last active administrator');
    expect(error.message).not.toContain('Internal backend text');
  });

  it('collects field errors of VALIDATION_FAILED', () => {
    const error = toApiError(
      httpError(400, {
        code: 'VALIDATION_FAILED',
        errors: [
          { field: 'email', message: 'must be a well-formed email address' },
          { field: 'username', message: 'invalid' },
        ],
      }),
    );

    expect(error.fieldErrors).toEqual({ email: 'must be a well-formed email address', username: 'invalid' });
  });

  it('falls back to the status when there is no code', () => {
    expect(toApiError(httpError(404)).message).toBe('Not found.');
    expect(toApiError(httpError(0)).message).toContain('temporarily unavailable');
    expect(toApiError(httpError(503, 'Service Unavailable')).message).toContain('temporarily unavailable');
  });

  it('adds the request id to server errors only', () => {
    const serverError = toApiError(httpError(500, { code: 'INTERNAL_ERROR' }, { 'X-Correlation-Id': 'corr-42' }));
    const clientError = toApiError(httpError(404, null, { 'X-Correlation-Id': 'corr-43' }));

    expect(serverError.correlationId).toBe('corr-42');
    expect(serverError.message).toContain('Request ID: corr-42');
    expect(clientError.correlationId).toBeNull();
  });

  it('handles non-HTTP errors', () => {
    expect(toApiError(new Error('boom')).message).toBe('Unexpected error.');
  });
});

describe('isAccessDenied', () => {
  it('is true only for a 403 without a domain code', () => {
    expect(isAccessDenied(httpError(403, { status: 403, detail: 'Access is denied' }))).toBe(true);
    expect(isAccessDenied(httpError(403, null))).toBe(true);
    expect(isAccessDenied(httpError(403, { code: 'SELF_ROLE_CHANGE' }))).toBe(false);
    expect(isAccessDenied(httpError(401))).toBe(false);
  });
});
