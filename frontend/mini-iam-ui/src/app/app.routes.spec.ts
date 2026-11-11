import { Route } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { routes } from './app.routes';

function rolesOf(path: string): string[] | undefined {
  const route = routes[0].children!.find((child: Route) => child.path === path);
  expect(route, `route ${path}`).toBeDefined();
  return route!.data?.['roles'] as string[] | undefined;
}

describe('route permissions (spec §2.1)', () => {
  it('restricts views to the roles of the matrix', () => {
    expect(rolesOf('dashboard')).toBeUndefined();
    expect(rolesOf('access-denied')).toBeUndefined();
    for (const path of ['users', 'users/new', 'users/:id', 'users/:id/edit']) {
      expect(rolesOf(path)).toEqual(['ADMIN', 'MANAGER']);
    }
    expect(rolesOf('roles')).toEqual(['ADMIN']);
    expect(rolesOf('audit-logs')).toEqual(['ADMIN', 'AUDITOR']);
  });

  it('guards the whole shell', () => {
    expect(routes[0].canActivate?.length).toBe(1);
    expect(routes[0].canActivateChild?.length).toBe(1);
  });
});
