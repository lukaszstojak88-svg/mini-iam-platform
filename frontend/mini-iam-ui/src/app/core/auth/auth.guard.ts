import { ActivatedRouteSnapshot, CanActivateFn, DefaultUrlSerializer, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';

const ACCESS_DENIED_URL = new DefaultUrlSerializer().parse('/access-denied');

/**
 * Decides whether a route can be activated:
 * - not logged in -> redirect to the Keycloak login page (Authorization Code + PKCE),
 * - `route.data.roles` set and the user has none of them -> Access Denied view,
 * - otherwise allowed.
 */
export async function isAccessAllowed(
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
  authData: AuthGuardData,
): Promise<boolean | UrlTree> {
  const { authenticated, grantedRoles, keycloak } = authData;

  if (!authenticated) {
    await keycloak.login({ redirectUri: window.location.origin + state.url });
    return false;
  }

  const requiredRoles = (route.data?.['roles'] ?? []) as string[];
  if (requiredRoles.length === 0 || requiredRoles.some((role) => grantedRoles.realmRoles.includes(role))) {
    return true;
  }
  return ACCESS_DENIED_URL;
}

export const authGuard = createAuthGuard<CanActivateFn>(isAccessAllowed);
