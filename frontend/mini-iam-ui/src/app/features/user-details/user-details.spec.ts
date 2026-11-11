import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { authAs, byTestId, notifyStub, problem, user } from '../../../testing/test-helpers';
import { RoleApiService } from '../../core/api/role-api.service';
import { UserApiService } from '../../core/api/user-api.service';
import { AppRole } from '../../core/auth/app-role';
import { AuthService } from '../../core/auth/auth.service';
import { ConfirmService } from '../../shared/confirm-dialog';
import { NotifyService } from '../../shared/notify.service';
import { UserDetails } from './user-details';

async function render(roles: AppRole[], userApi: Partial<UserApiService> = {}, confirmed = true, me = 'kc-admin') {
  const notify = notifyStub();
  const roleApi = { getUserRoles: vi.fn(() => of({ userId: 'kc-1', roles: ['MANAGER', 'USER'] as AppRole[] })) };
  const confirm = { confirm: vi.fn(() => of(confirmed)) };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'users/:id', component: UserDetails }, { path: 'users', children: [] }], withComponentInputBinding()),
      { provide: UserApiService, useValue: { get: vi.fn(() => of(user())), ...userApi } },
      { provide: RoleApiService, useValue: roleApi },
      { provide: AuthService, useValue: authAs(roles, me) },
      { provide: ConfirmService, useValue: confirm },
      { provide: NotifyService, useValue: notify },
    ],
  });
  const harness = await RouterTestingHarness.create('/users/id-1');
  return { harness, root: harness.routeNativeElement!, notify, roleApi, confirm };
}

describe('UserDetails', () => {
  it('shows roles (by keycloakId) and admin actions to ADMIN', async () => {
    const { root, roleApi } = await render(['ADMIN', 'USER']);

    expect(byTestId(root, 'details')?.textContent).toContain('jan@example.com');
    expect(roleApi.getUserRoles).toHaveBeenCalledWith('kc-1');
    expect(byTestId(root, 'roles')?.textContent).toContain('MANAGER');
    expect(byTestId(root, 'toggle-status')).not.toBeNull();
    expect(byTestId(root, 'delete')).not.toBeNull();
    expect(byTestId(root, 'edit')).not.toBeNull();
  });

  it('lets MANAGER edit but not change status, delete or see roles', async () => {
    const { root, roleApi } = await render(['MANAGER', 'USER']);

    expect(byTestId(root, 'edit')).not.toBeNull();
    expect(byTestId(root, 'toggle-status')).toBeNull();
    expect(byTestId(root, 'delete')).toBeNull();
    expect(byTestId(root, 'roles')).toBeNull();
    expect(roleApi.getUserRoles).not.toHaveBeenCalled();
  });

  it('deactivates after confirmation', async () => {
    const setEnabled = vi.fn(() => of(user({ enabled: false })));
    const { harness, root, notify, confirm } = await render(['ADMIN'], { setEnabled });

    byTestId(root, 'toggle-status')!.click();
    harness.detectChanges();

    expect(confirm.confirm).toHaveBeenCalled();
    expect(setEnabled).toHaveBeenCalledWith('id-1', false);
    expect(notify.success).toHaveBeenCalledWith('Account deactivated.');
    expect(byTestId(root, 'details')?.textContent).toContain('Inactive');
  });

  it('hides deactivate and delete on the own account', async () => {
    const { root } = await render(['ADMIN', 'USER'], {}, true, 'kc-1');

    expect(byTestId(root, 'toggle-status')).toBeNull();
    expect(byTestId(root, 'delete')).toBeNull();
    expect(byTestId(root, 'edit')).not.toBeNull();
    expect(byTestId(root, 'self-note')).not.toBeNull();
  });

  it('does nothing when the confirmation is cancelled', async () => {
    const remove = vi.fn(() => of(undefined));
    const { root } = await render(['ADMIN'], { delete: remove }, false);

    byTestId(root, 'delete')!.click();

    expect(remove).not.toHaveBeenCalled();
  });

  it('deletes and returns to the list', async () => {
    const remove = vi.fn(() => of(undefined));
    const { harness, root } = await render(['ADMIN'], { delete: remove });

    byTestId(root, 'delete')!.click();
    await harness.fixture.whenStable();

    expect(remove).toHaveBeenCalledWith('id-1');
    expect(TestBed.inject(Router).url).toBe('/users');
  });

  it('shows the last-administrator rule as a message', async () => {
    const setEnabled = vi.fn(() => throwError(() => problem(409, 'LAST_ADMIN')));
    const { notify, root } = await render(['ADMIN'], { setEnabled });

    byTestId(root, 'toggle-status')!.click();

    expect(notify.error).toHaveBeenCalledWith(expect.stringContaining('last active administrator'));
  });

  it('shows an error state for a missing user', async () => {
    const { root } = await render(['ADMIN'], { get: vi.fn(() => throwError(() => problem(404, 'USER_NOT_FOUND'))) });

    expect(byTestId(root, 'error')?.textContent).toContain('User not found.');
  });
});
