import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { byTestId, notifyStub, problem, user, userDetails } from '../../../testing/test-helpers';
import { UserApiService } from '../../core/api/user-api.service';
import { NotifyService } from '../../shared/notify.service';
import { UserForm } from './user-form';

async function render(url: string, userApi: Partial<UserApiService>) {
  const notify = notifyStub();
  TestBed.configureTestingModule({
    providers: [
      provideRouter(
        [
          { path: 'users/new', component: UserForm },
          { path: 'users/:id/edit', component: UserForm },
          { path: 'users/:id', children: [] },
        ],
        withComponentInputBinding(),
      ),
      { provide: UserApiService, useValue: userApi },
      { provide: NotifyService, useValue: notify },
    ],
  });
  const harness = await RouterTestingHarness.create(url);
  return { harness, root: harness.routeNativeElement!, notify };
}

function type(root: HTMLElement, name: string, value: string): void {
  const input = root.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

function fillValidCreateForm(root: HTMLElement): void {
  type(root, 'username', 'jan.kowalski');
  type(root, 'email', 'jan@example.com');
  type(root, 'firstName', ' Jan ');
  type(root, 'lastName', 'Kowalski');
  type(root, 'temporaryPassword', 'Secret123!');
  type(root, 'confirmPassword', 'Secret123!');
}

const submit = (root: HTMLElement) => root.querySelector('form')!.dispatchEvent(new Event('submit'));

describe('UserForm', () => {
  it('creates a user, clears the temporary password and opens the new profile', async () => {
    const create = vi.fn(() => of(user({ id: 'id-9', username: 'jan.kowalski' })));
    const { harness, root, notify } = await render('/users/new', { create });

    fillValidCreateForm(root);
    submit(root);
    await harness.fixture.whenStable();

    expect(create).toHaveBeenCalledWith({
      username: 'jan.kowalski',
      email: 'jan@example.com',
      firstName: 'Jan',
      lastName: 'Kowalski',
      temporaryPassword: 'Secret123!',
    });
    expect(notify.success).toHaveBeenCalled();
    expect(TestBed.inject(Router).url).toBe('/users/id-9');
  });

  it('does not submit an invalid form and shows field errors', async () => {
    const create = vi.fn();
    const { harness, root } = await render('/users/new', { create });

    type(root, 'username', 'a b');
    type(root, 'email', 'not-an-email');
    submit(root);
    harness.detectChanges();

    expect(create).not.toHaveBeenCalled();
    expect(root.textContent).toContain("3-100 characters: letters, digits, '.', '_' or '-'.");
    expect(root.textContent).toContain('Enter a valid e-mail address.');
  });

  it('requires first and last name (not just white space)', async () => {
    const create = vi.fn();
    const { harness, root } = await render('/users/new', { create });

    fillValidCreateForm(root);
    type(root, 'firstName', '   ');
    type(root, 'lastName', '');
    submit(root);
    harness.detectChanges();

    expect(create).not.toHaveBeenCalled();
    expect(root.textContent).toContain('First name is required.');
    expect(root.textContent).toContain('Last name is required.');
  });

  it('requires matching passwords', async () => {
    const create = vi.fn();
    const { harness, root } = await render('/users/new', { create });

    fillValidCreateForm(root);
    type(root, 'confirmPassword', 'Different1!');
    submit(root);
    harness.detectChanges();

    expect(create).not.toHaveBeenCalled();
    expect(byTestId(root, 'password-mismatch')).not.toBeNull();
  });

  it('marks username and e-mail when they are taken (409) and clears the password', async () => {
    const create = vi.fn(() => throwError(() => problem(409, 'USERNAME_OR_EMAIL_TAKEN')));
    const { harness, root } = await render('/users/new', { create });

    fillValidCreateForm(root);
    submit(root);
    harness.detectChanges();

    expect(byTestId(root, 'form-error')?.textContent).toContain('already in use');
    expect(root.querySelectorAll('mat-error').length).toBeGreaterThanOrEqual(2);
    expect((byTestId(root, 'password') as HTMLInputElement).value).toBe('');
  });

  it('shows server validation errors next to the fields', async () => {
    const create = vi.fn(() =>
      throwError(() => problem(400, 'VALIDATION_FAILED', { errors: [{ field: 'email', message: 'must be a well-formed email address' }] })),
    );
    const { harness, root } = await render('/users/new', { create });

    fillValidCreateForm(root);
    submit(root);
    harness.detectChanges();

    expect(root.textContent).toContain('Rejected by the server: must be a well-formed email address.');
  });

  it('edits a user without password fields and with a read-only username', async () => {
    const update = vi.fn(() => of(user({ email: 'new@example.com' })));
    const { root } = await render('/users/id-1/edit', { get: vi.fn(() => of(userDetails())), update });

    expect(byTestId(root, 'password')).toBeNull();
    expect(root.querySelector<HTMLInputElement>('input[formcontrolname="username"]')!.disabled).toBe(true);

    type(root, 'email', 'new@example.com');
    submit(root);

    expect(update).toHaveBeenCalledWith('id-1', { email: 'new@example.com', firstName: 'Jan', lastName: 'Kowalski' });
  });

  it('explains when a manager tries to edit an administrator', async () => {
    const update = vi.fn(() => throwError(() => problem(403, 'ADMIN_ACCOUNT_PROTECTED')));
    const { harness, root } = await render('/users/id-1/edit', { get: vi.fn(() => of(userDetails())), update });

    submit(root);
    harness.detectChanges();

    expect(byTestId(root, 'form-error')?.textContent).toContain('Managers cannot modify administrator accounts.');
  });

  it('shows no form when the profile is not editable for the caller', async () => {
    const update = vi.fn();
    const { root } = await render('/users/id-1/edit', {
      get: vi.fn(() => of(userDetails({ editable: false }))),
      update,
    });

    expect(byTestId(root, 'not-editable')?.textContent).toContain('Managers cannot modify administrator accounts.');
    expect(root.querySelector('form')).toBeNull();
    expect(update).not.toHaveBeenCalled();
  });
});
