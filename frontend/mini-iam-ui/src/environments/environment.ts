// Local development configuration. Contains no secrets: the SPA is a public OIDC client (spec §5.1).
export const environment = {
  keycloak: {
    url: 'http://localhost:8080',
    realm: 'mini-iam',
    clientId: 'mini-iam-frontend',
  },
  api: {
    userService: 'http://localhost:8081',
    authService: 'http://localhost:8082',
    auditService: 'http://localhost:8083',
  },
  // Access token is attached only to requests matching this pattern (never to Keycloak or third parties).
  apiUrlPattern: /^http:\/\/localhost:808[1-3]\/api(\/.*)?$/,
  // Inactivity after which the user is logged out instead of silently refreshing the token.
  sessionIdleTimeoutMs: 30 * 60 * 1000,
};
