import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { isAccessDenied } from './api-error';

/**
 * Global handling of authorization errors from our APIs (spec §10.2):
 * - 401 (missing/expired token) -> re-authenticate in Keycloak;
 * - 403 without a `code` (Spring Security: missing role) -> Access Denied view.
 * A 403 with a domain `code` (e.g. SELF_ROLE_CHANGE, ADMIN_ACCOUNT_PROTECTED) is a business rule and is left to the
 * view, which shows it as a message. The error is always re-thrown so components can show their own error state.
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloak = inject(Keycloak);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && environment.apiUrlPattern.test(req.url)) {
        if (error.status === 401) {
          void keycloak.login({ redirectUri: window.location.href });
        } else if (isAccessDenied(error)) {
          void router.navigateByUrl('/access-denied');
        }
      }
      return throwError(() => error);
    }),
  );
};
