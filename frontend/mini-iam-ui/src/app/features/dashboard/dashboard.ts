import { Component, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { forkJoin } from 'rxjs';

import { User } from '../../core/api/api-models';
import { AuthApiService, CurrentUser } from '../../core/api/auth-api.service';
import { UserApiService } from '../../core/api/user-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError, toApiError } from '../../core/http/api-error';
import { LoadState, LoadStatus } from '../../shared/load-state';
import { NotifyService } from '../../shared/notify.service';
import { PageHeader } from '../../shared/page-header';
import { StatTile } from '../../shared/stat-tile';
import { StatusPill } from '../../shared/status-pill';
import { applyFieldErrors, notBlank } from '../../shared/form-errors';

/**
 * The signed-in user's view: identity and roles from the token (`GET /api/auth/me`), the profile
 * (`GET /api/users/me`, created just in time) and editing of own e-mail and names (`PUT /api/users/me`).
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    LoadState,
    PageHeader,
    StatTile,
    StatusPill,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly userApi = inject(UserApiService);
  private readonly notify = inject(NotifyService);
  private readonly fb = inject(NonNullableFormBuilder);

  /** PUT /api/users/me is allowed for ADMIN, MANAGER and USER (every account has the base role USER). */
  protected readonly canEditProfile = inject(AuthService).hasAnyRole(['ADMIN', 'MANAGER', 'USER']);

  protected readonly status = signal<LoadStatus>('loading');
  protected readonly loadError = signal('');
  protected readonly identity = signal<CurrentUser | null>(null);
  protected readonly profile = signal<User | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<ApiError | null>(null);

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    firstName: ['', [Validators.required, notBlank, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, notBlank, Validators.maxLength(100)]],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    forkJoin({ identity: this.authApi.getCurrentUser(), profile: this.userApi.getMe() }).subscribe({
      next: ({ identity, profile }) => {
        this.identity.set(identity);
        this.setProfile(profile);
        this.status.set('loaded');
      },
      error: (error: unknown) => {
        this.loadError.set(toApiError(error).message);
        this.status.set('error');
      },
    });
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.saveError.set(null);
    this.userApi
      .updateMe({ email: value.email.trim(), firstName: value.firstName.trim(), lastName: value.lastName.trim() })
      .subscribe({
        next: (profile) => {
          this.saving.set(false);
          this.setProfile(profile);
          this.notify.success('Profile saved.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const apiError = toApiError(error);
          applyFieldErrors(this.form, apiError);
          this.saveError.set(apiError);
        },
      });
  }

  protected reset(): void {
    const profile = this.profile();
    if (profile) {
      this.setProfile(profile);
    }
  }

  private setProfile(profile: User): void {
    this.profile.set(profile);
    this.saveError.set(null);
    this.form.reset({ email: profile.email, firstName: profile.firstName ?? '', lastName: profile.lastName ?? '' });
  }
}
