import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { authAs, byTestId, notifyStub, problem, user } from '../../../testing/test-helpers';
import { Role, UserRoles } from '../../core/api/api-models';
import { RoleApiService } from '../../core/api/role-api.service';
import { UserApiService } from '../../core/api/user-api.service';
import { AppRole } from '../../core/auth/app-role';
import { AuthService } from '../../core/auth/auth.service';
import { ConfirmService } from '../../shared/confirm-dialog';
import { NotifyService } from '../../shared/notify.service';
import { Roles } from './roles';

const ROLES: Role[] = [
  { name: 'ADMIN', description: 'Admin', assignable: true },
  { name: 'MANAGER', description: 'Manager', assignable: true },
  { name: 'USER', description: 'Base', assignable: false },
  { name: 'AUDITOR', description: 'Auditor', assignable: true },
];

async function render(options: { held?: AppRole[]; me?: string; roleApi?: Partial<RoleApiService> } = {}) {
  const notify = notifyStub();
  const roleApi = {
    listRoles: vi.fn(() => of(ROLES)),
    getUserRoles: vi.fn(() => of({ userId: 'kc-1', roles: options.held ?? (['MANAGER', 'USER'] as AppRole[]) })),
    assign: vi.fn(),
    remove: vi.fn(() => of(undefined)),
    ...options.roleApi,
  };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'roles', component: Roles }]),
      { provide: RoleApiService, useValue: roleApi },
      { provide: UserApiService, useValue: { get: vi.fn(() => of(user())), list: vi.fn() } },
      { provide: AuthService, useValue: authAs(['ADMIN', 'USER'], options.me ?? 'kc-admin') },
      { provide: ConfirmService, useValue: { confirm: vi.fn(() => of(true)) } },
      { provide: NotifyService, useValue: notify },
    ],
  });
  // Pre-selected user, as when coming from User Details.
  const harness = await RouterTestingHarness.create('/roles?user=id-1');
  return { harness, root: harness.routeNativeElement!, roleApi, notify };
}

describe('Roles', () => {
  it('lists application roles and the selected user roles; USER cannot be removed', async () => {
    const { root, roleApi } = await render();

    expect(byTestId(root, 'roles-table')?.textContent).toContain('Base role (automatic)');
    expect(roleApi.getUserRoles).toHaveBeenCalledWith('kc-1');
    expect(byTestId(root, 'role-chips')?.textContent).toContain('MANAGER');
    expect(byTestId(root, 'remove-MANAGER')).not.toBeNull();
    expect(byTestId(root, 'remove-USER')).toBeNull();
  });

  it('assigns a role and shows the roles returned by the API', async () => {
    const assign = vi.fn(() => of({ userId: 'kc-1', roles: ['AUDITOR', 'MANAGER', 'USER'] as AppRole[] }));
    const { harness, root, notify } = await render({ roleApi: { assign } });
    const component = harness.routeDebugElement!.componentInstance as Roles;

    (component as unknown as { roleToAssign: { setValue(v: AppRole): void } }).roleToAssign.setValue('AUDITOR');
    harness.detectChanges();
    byTestId(root, 'assign')!.click();
    harness.detectChanges();

    expect(assign).toHaveBeenCalledWith('kc-1', 'AUDITOR');
    expect(byTestId(root, 'role-chips')?.textContent).toContain('AUDITOR');
    expect(notify.success).toHaveBeenCalledWith(expect.stringContaining('up to 5 minutes'));
  });

  it('removes a role after confirmation', async () => {
    const { harness, root, roleApi } = await render();

    byTestId(root, 'remove-MANAGER')!.click();
    harness.detectChanges();

    expect(roleApi.remove).toHaveBeenCalledWith('kc-1', 'MANAGER');
    expect(byTestId(root, 'role-chips')?.textContent).not.toContain('MANAGER');
  });

  it('blocks changes of own roles', async () => {
    const { root } = await render({ me: 'kc-1' });

    expect(byTestId(root, 'self-note')).not.toBeNull();
    expect(byTestId(root, 'remove-MANAGER')).toBeNull();
    expect(byTestId(root, 'assign')).toBeNull();
  });

  it('never shows the roles of a previously selected user (out-of-order responses)', async () => {
    const responses: Record<string, Subject<UserRoles>> = { 'kc-1': new Subject(), 'kc-a': new Subject(), 'kc-b': new Subject() };
    const getUserRoles = vi.fn((keycloakId: string) => responses[keycloakId]);
    const { harness, root } = await render({ roleApi: { getUserRoles } });
    const component = harness.routeDebugElement!.componentInstance as unknown as {
      onUserSelected(event: { option: { value: unknown } }): void;
    };

    component.onUserSelected({ option: { value: user({ id: 'id-a', keycloakId: 'kc-a', username: 'anna' }) } });
    component.onUserSelected({ option: { value: user({ id: 'id-b', keycloakId: 'kc-b', username: 'bob' }) } });
    responses['kc-b'].next({ userId: 'kc-b', roles: ['USER'] });
    responses['kc-a'].next({ userId: 'kc-a', roles: ['ADMIN', 'USER'] });
    harness.detectChanges();

    const chips = byTestId(root, 'role-chips')?.textContent ?? '';
    expect(chips).toContain('USER');
    expect(chips).not.toContain('ADMIN');
  });

  it('takes assignable roles from the API', async () => {
    const roles = ROLES.map((role) => (role.name === 'AUDITOR' ? { ...role, assignable: false } : role));
    const { harness } = await render({ held: ['AUDITOR', 'USER'], roleApi: { listRoles: vi.fn(() => of(roles)) } });

    expect(byTestId(harness.routeNativeElement!, 'remove-AUDITOR')).toBeNull();
  });

  it('shows business rule errors as messages', async () => {
    const remove = vi.fn(() => throwError(() => problem(409, 'LAST_ADMIN')));
    const { root, notify } = await render({ held: ['ADMIN', 'USER'], roleApi: { remove } });

    byTestId(root, 'remove-ADMIN')!.click();

    expect(notify.error).toHaveBeenCalledWith(expect.stringContaining('last active administrator'));
  });
});
