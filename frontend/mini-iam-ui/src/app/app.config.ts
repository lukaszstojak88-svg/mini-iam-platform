import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import {
  AutoRefreshTokenService,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  createInterceptorCondition,
  IncludeBearerTokenCondition,
  includeBearerTokenInterceptor,
  provideKeycloak,
  UserActivityService,
  withAutoRefreshToken,
} from 'keycloak-angular';

import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { httpErrorInterceptor } from './core/http/http-error.interceptor';

const apiBearerCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: environment.apiUrlPattern,
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideKeycloak({
      config: environment.keycloak,
      initOptions: {
        // Authorization Code Flow with PKCE (spec §5.2); login is triggered by the route guard.
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        checkLoginIframe: false,
      },
      features: [
        withAutoRefreshToken({
          onInactivityTimeout: 'logout',
          sessionTimeout: environment.sessionIdleTimeoutMs,
          logoutOptions: { redirectUri: window.location.origin },
        }),
      ],
      providers: [AutoRefreshTokenService, UserActivityService],
    }),
    { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [apiBearerCondition] },
    provideHttpClient(withInterceptors([includeBearerTokenInterceptor, httpErrorInterceptor])),
    provideRouter(routes),
  ],
};
