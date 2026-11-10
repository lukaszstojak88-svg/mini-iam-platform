import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';

/**
 * Global handling of authorization errors from our APIs (spec §10.2):
 * 401 (missing/expired token) -> re-authenticate in Keycloak, 403 -> Access Denied view.
 * The error is re-thrown so components can still show their own error state.
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloak = inject(Keycloak);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && environment.apiUrlPattern.test(req.url)) {
        if (error.status === 401) {
          void keycloak.login({ redirectUri: window.location.href });
        } else if (error.status === 403) {
          void router.navigateByUrl('/access-denied');
        }
      }
      return throwError(() => error);
    }),
  );
};
