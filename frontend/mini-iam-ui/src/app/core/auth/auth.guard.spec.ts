import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthGuardData } from 'keycloak-angular';
import Keycloak from 'keycloak-js';
import { describe, expect, it, vi } from 'vitest';

import { isAccessAllowed } from './auth.guard';

function routeWithRoles(roles?: string[]): ActivatedRouteSnapshot {
  return { data: roles ? { roles } : {} } as unknown as ActivatedRouteSnapshot;
}

function authData(authenticated: boolean, realmRoles: string[] = []) {
  const keycloak = { login: vi.fn().mockResolvedValue(undefined) } as unknown as Keycloak;
  const data: AuthGuardData = { authenticated, grantedRoles: { realmRoles, resourceRoles: {} }, keycloak };
  return { data, keycloak };
}

const state = { url: '/dashboard' } as RouterStateSnapshot;

describe('isAccessAllowed', () => {
  it('redirects anonymous users to Keycloak login and blocks navigation', async () => {
    const { data, keycloak } = authData(false);

    const result = await isAccessAllowed(routeWithRoles(), state, data);

    expect(result).toBe(false);
    expect(keycloak.login).toHaveBeenCalledWith({ redirectUri: `${window.location.origin}/dashboard` });
  });

  it('allows authenticated users on routes without role requirements', async () => {
    const { data } = authData(true, ['USER']);

    expect(await isAccessAllowed(routeWithRoles(), state, data)).toBe(true);
  });

  it('allows users having any of the required roles', async () => {
    const { data } = authData(true, ['USER', 'AUDITOR']);

    expect(await isAccessAllowed(routeWithRoles(['ADMIN', 'AUDITOR']), state, data)).toBe(true);
  });

  it('sends users without required roles to the access denied view', async () => {
    const { data, keycloak } = authData(true, ['USER']);

    const result = await isAccessAllowed(routeWithRoles(['ADMIN']), state, data);

    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/access-denied');
    expect(keycloak.login).not.toHaveBeenCalled();
  });
});
