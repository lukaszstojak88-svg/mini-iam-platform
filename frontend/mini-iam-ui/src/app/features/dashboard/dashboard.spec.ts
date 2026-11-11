import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { authAs, byTestId, notifyStub, problem, user } from '../../../testing/test-helpers';
import { AuthApiService, CurrentUser } from '../../core/api/auth-api.service';
import { UserApiService } from '../../core/api/user-api.service';
import { AppRole } from '../../core/auth/app-role';
import { AuthService } from '../../core/auth/auth.service';
import { NotifyService } from '../../shared/notify.service';
import { Dashboard } from './dashboard';

const ME: CurrentUser = {
  userId: 'kc-1',
  username: 'jan',
  email: 'jan@example.com',
  firstName: 'Jan',
  lastName: 'Kowalski',
  roles: ['ADMIN', 'USER'],
};

function render(options: { identity?: unknown; userApi?: Partial<UserApiService>; roles?: AppRole[] } = {}) {
  const notify = notifyStub();
  const userApi = { getMe: () => of(user()), updateMe: vi.fn(), ...options.userApi };
  TestBed.configureTestingModule({
    imports: [Dashboard],
    providers: [
      { provide: AuthApiService, useValue: { getCurrentUser: () => options.identity ?? of(ME) } },
      { provide: UserApiService, useValue: userApi },
      { provide: AuthService, useValue: authAs(options.roles ?? ['ADMIN', 'USER']) },
      { provide: NotifyService, useValue: notify },
    ],
  });
  const fixture = TestBed.createComponent(Dashboard);
  fixture.detectChanges();
  return { fixture, root: fixture.nativeElement as HTMLElement, notify, userApi };
}

describe('Dashboard', () => {
  it('shows a loading indicator, then the profile with roles from the token', async () => {
    const identity = new Subject<CurrentUser>();
    const { fixture, root } = render({ identity });

    expect(byTestId(root, 'loading')).not.toBeNull();

    identity.next(ME);
    identity.complete();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(byTestId(root, 'loading')).toBeNull();
    const profile = byTestId(root, 'profile');
    expect(profile?.textContent).toContain('jan@example.com');
    expect(profile?.textContent).toContain('ADMIN');
    expect(profile?.textContent).toContain('USER');
  });

  it('shows an error state with the API message when loading fails', async () => {
    const { fixture, root } = render({ identity: throwError(() => problem(503)) });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(byTestId(root, 'error')?.textContent).toContain('temporarily unavailable');
  });

  it('saves the own profile and shows a confirmation', async () => {
    const updateMe = vi.fn(() => of(user({ firstName: 'Janek' })));
    const { fixture, root, notify } = render({ userApi: { updateMe } });
    await fixture.whenStable();
    fixture.detectChanges();

    const firstName = root.querySelector<HTMLInputElement>('input[formcontrolname="firstName"]')!;
    firstName.value = ' Janek ';
    firstName.dispatchEvent(new Event('input'));
    root.querySelector<HTMLFormElement>('[data-testid="profile-form"] form')!.dispatchEvent(new Event('submit'));

    expect(updateMe).toHaveBeenCalledWith({ email: 'jan@example.com', firstName: 'Janek', lastName: 'Kowalski' });
    expect(notify.success).toHaveBeenCalledWith('Profile saved.');
  });

  it('shows a conflict on the form when the e-mail is taken', async () => {
    const updateMe = vi.fn(() => throwError(() => problem(409, 'USERNAME_OR_EMAIL_TAKEN')));
    const { fixture, root } = render({ userApi: { updateMe } });
    await fixture.whenStable();
    fixture.detectChanges();

    const email = root.querySelector<HTMLInputElement>('input[formcontrolname="email"]')!;
    email.value = 'taken@example.com';
    email.dispatchEvent(new Event('input'));
    root.querySelector<HTMLFormElement>('[data-testid="profile-form"] form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(root.querySelector('.form-error')?.textContent).toContain('already in use');
  });

  it('hides the profile form for accounts without the USER, MANAGER or ADMIN role', async () => {
    const { fixture, root } = render({ roles: ['AUDITOR'] });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(byTestId(root, 'profile')).not.toBeNull();
    expect(byTestId(root, 'profile-form')).toBeNull();
  });
});
