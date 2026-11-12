import { Component, inject, input, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { User } from '../../core/api/api-models';
import { UserApiService } from '../../core/api/user-api.service';
import { ApiError, toApiError } from '../../core/http/api-error';
import { applyFieldErrors, notBlank } from '../../shared/form-errors';
import { LoadState, LoadStatus } from '../../shared/load-state';
import { NotifyService } from '../../shared/notify.service';
import { PageHeader } from '../../shared/page-header';

/** Same rule as the backend: 3-100 characters, letters, digits, '.', '_' or '-' (stored lower-case). */
export const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,100}$/;

/** The temporary password and its confirmation must match (UI-only check). */
export const passwordsMatch: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('temporaryPassword')?.value as string | undefined;
  const confirmation = group.get('confirmPassword')?.value as string | undefined;
  return password && confirmation && password !== confirmation ? { passwordMismatch: true } : null;
};

/**
 * Create (`/users/new`) or edit (`/users/:id/edit`) a user (ADMIN, MANAGER). Validation mirrors the backend.
 * The temporary password exists only when creating: it goes to Keycloak (changed at first login) and is cleared
 * from the form right after the request. The username cannot be changed after creation; roles are not edited here.
 */
@Component({
  selector: 'app-user-form',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, LoadState, PageHeader],
  templateUrl: './user-form.html',
  styleUrl: './user-form.scss',
})
export class UserForm implements OnInit {
  /** Route parameter `:id`; absent when creating. */
  readonly id = input<string>();

  private readonly userApi = inject(UserApiService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly status = signal<LoadStatus>('loaded');
  protected readonly loadError = signal('');
  protected readonly saving = signal(false);
  protected readonly formError = signal<ApiError | null>(null);
  /** Set when the backend reports the profile as not editable for the caller (MANAGER and an ADMIN account). */
  protected readonly notEditable = signal(false);
  protected readonly hidePassword = signal(true);

  protected readonly form = this.fb.group(
    {
      username: ['', [Validators.required, Validators.pattern(USERNAME_PATTERN)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
      firstName: ['', [Validators.required, notBlank, Validators.maxLength(100)]],
      lastName: ['', [Validators.required, notBlank, Validators.maxLength(100)]],
      temporaryPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  get editing(): boolean {
    return !!this.id();
  }

  ngOnInit(): void {
    if (this.editing) {
      this.form.controls.username.disable();
      this.form.controls.temporaryPassword.disable();
      this.form.controls.confirmPassword.disable();
      this.load();
    }
  }

  protected load(): void {
    this.status.set('loading');
    this.userApi.get(this.id()!).subscribe({
      next: (user) => {
        this.notEditable.set(!user.editable);
        this.form.patchValue({
          username: user.username,
          email: user.email,
          firstName: user.firstName ?? '',
          lastName: user.lastName ?? '',
        });
        this.status.set('loaded');
      },
      error: (error: unknown) => {
        this.loadError.set(toApiError(error).message);
        this.status.set('error');
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const profile = { email: value.email.trim(), firstName: value.firstName.trim(), lastName: value.lastName.trim() };
    const request$: Observable<User> = this.editing
      ? this.userApi.update(this.id()!, profile)
      : this.userApi.create({ ...profile, username: value.username.trim(), temporaryPassword: value.temporaryPassword });

    this.saving.set(true);
    this.formError.set(null);
    request$.subscribe({
      next: (user) => {
        this.saving.set(false);
        this.clearPasswords();
        this.notify.success(this.editing ? 'User saved.' : `User ${user.username} created. They must change the temporary password at first sign-in.`);
        void this.router.navigate(['/users', user.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.clearPasswords();
        const apiError = toApiError(error);
        applyFieldErrors(this.form, apiError);
        this.formError.set(apiError);
      },
    });
  }

  /** The temporary password is never kept in the form after a request (success or failure). */
  private clearPasswords(): void {
    if (!this.editing) {
      this.form.controls.temporaryPassword.reset('');
      this.form.controls.confirmPassword.reset('');
    }
  }
}
