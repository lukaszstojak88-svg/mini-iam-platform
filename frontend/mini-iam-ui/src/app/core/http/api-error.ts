import { HttpErrorResponse } from '@angular/common/http';

/** Error of an API call, translated for display. Backend `detail` texts are never shown as they are. */
export interface ApiError {
  status: number;
  /** Machine-readable `code` from the problem detail, if any. */
  code: string | null;
  /** User-facing message. */
  message: string;
  /** Field -> message, from `VALIDATION_FAILED` problem details. */
  fieldErrors: Record<string, string>;
  /** `X-Correlation-Id` of a failed request (shown for server errors to help find it in the logs). */
  correlationId: string | null;
}

const CODE_MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: 'Some fields are invalid. Check the form and try again.',
  INVALID_REQUEST: 'The request was not accepted. Check the entered values.',
  INVALID_USER_DATA: 'The identity provider rejected the data (e.g. the password does not meet the policy).',
  USER_NOT_FOUND: 'User not found.',
  RESOURCE_NOT_FOUND: 'Not found.',
  AUDIT_EVENT_NOT_FOUND: 'Audit event not found.',
  USERNAME_OR_EMAIL_TAKEN: 'This username or e-mail is already in use.',
  LAST_ADMIN: 'This is the last active administrator - the operation would leave the system without one.',
  ADMIN_ACCOUNT_PROTECTED: 'Managers cannot modify administrator accounts.',
  SELF_ACCOUNT_PROTECTED: 'You cannot deactivate or delete your own account - ask another administrator.',
  USER_SYNC_FAILED: 'The operation could not be completed consistently. Contact the administrator.',
  ROLE_NOT_ASSIGNABLE: 'This role cannot be assigned or removed.',
  ROLE_NOT_FOUND: 'The role does not exist.',
  ROLE_NOT_ASSIGNED: 'The user does not have this role assigned directly.',
  ROLE_ALREADY_ASSIGNED: 'The user already has this role.',
  SELF_ROLE_CHANGE: 'You cannot change your own roles - ask another administrator.',
  SERVICE_ACCOUNT_PROTECTED: 'Roles of technical (service) accounts cannot be changed.',
  KEYCLOAK_UNAVAILABLE: 'The identity provider is temporarily unavailable. Try again later.',
  KEYCLOAK_INTEGRATION_ERROR: 'The identity provider rejected the request. Contact the administrator.',
  INTERNAL_ERROR: 'Unexpected server error.',
};

function statusMessage(status: number): string {
  if (status === 0 || status === 502 || status === 503 || status === 504) {
    return 'Service temporarily unavailable. Try again later.';
  }
  if (status === 400) {
    return 'The request was not accepted. Check the entered values.';
  }
  if (status === 401) {
    return 'Your session has expired. Please sign in again.';
  }
  if (status === 403) {
    return 'You do not have permission to perform this operation.';
  }
  if (status === 404) {
    return 'Not found.';
  }
  if (status === 409) {
    return 'The operation conflicts with the current state. Refresh and try again.';
  }
  return 'Unexpected error.';
}

/** Translates any error from an API call into an {@link ApiError}. */
export function toApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: -1, code: null, message: 'Unexpected error.', fieldErrors: {}, correlationId: null };
  }
  const body = (typeof error.error === 'object' && error.error !== null ? error.error : {}) as Record<string, unknown>;
  const code = typeof body['code'] === 'string' ? body['code'] : null;

  const fieldErrors: Record<string, string> = {};
  if (Array.isArray(body['errors'])) {
    for (const entry of body['errors'] as { field?: unknown; message?: unknown }[]) {
      if (typeof entry.field === 'string') {
        fieldErrors[entry.field] = typeof entry.message === 'string' ? entry.message : 'invalid value';
      }
    }
  }

  let message = (code && CODE_MESSAGES[code]) || statusMessage(error.status);
  const correlationId = error.status >= 500 ? error.headers?.get('X-Correlation-Id') ?? null : null;
  if (correlationId) {
    message += ` Request ID: ${correlationId}`;
  }
  return { status: error.status, code, message, fieldErrors, correlationId };
}

/** True for a 403 issued by Spring Security (no `code`): the caller lacks the required role. */
export function isAccessDenied(error: HttpErrorResponse): boolean {
  if (error.status !== 403) {
    return false;
  }
  const body = error.error as Record<string, unknown> | null;
  return !(body && typeof body === 'object' && typeof body['code'] === 'string');
}
