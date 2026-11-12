import { DatePipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';

import { User, UserDetails as UserProfileDetails, UserRoles } from '../../core/api/api-models';
import { RoleApiService } from '../../core/api/role-api.service';
import { UserApiService } from '../../core/api/user-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { toApiError } from '../../core/http/api-error';
import { ConfirmService } from '../../shared/confirm-dialog';
import { LoadState, LoadStatus } from '../../shared/load-state';
import { NotifyService } from '../../shared/notify.service';
import { PageHeader } from '../../shared/page-header';
import { StatusPill } from '../../shared/status-pill';

/**
 * Profile of one user. ADMIN and MANAGER can open it and edit the profile (Edit is hidden when the backend reports
 * the profile as not editable, i.e. a MANAGER viewing an ADMIN account); only ADMIN sees the roles and can
 * activate, deactivate or delete the account (always after confirmation). Roles are read from auth-service by
 * the `keycloakId`, not the local profile id.
 */
@Component({
  selector: 'app-user-details',
  imports: [DatePipe, RouterLink, MatButtonModule, MatChipsModule, MatIconModule, LoadState, PageHeader, StatusPill],
  templateUrl: './user-details.html',
})
export class UserDetails implements OnInit {
  /** Route parameter `:id` (local profile id). */
  readonly id = input.required<string>();

  private readonly userApi = inject(UserApiService);
  private readonly roleApi = inject(RoleApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);

  private readonly auth = inject(AuthService);
  protected readonly isAdmin = this.auth.hasAnyRole(['ADMIN']);

  protected readonly status = signal<LoadStatus>('loading');
  protected readonly errorMessage = signal('');
  protected readonly user = signal<UserProfileDetails | null>(null);
  protected readonly roles = signal<UserRoles | null>(null);
  protected readonly rolesError = signal('');
  protected readonly busy = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    this.userApi.get(this.id()).subscribe({
      next: (user) => {
        this.user.set(user);
        this.status.set('loaded');
        if (this.isAdmin) {
          this.loadRoles(user.keycloakId);
        }
      },
      error: (error: unknown) => {
        this.errorMessage.set(toApiError(error).message);
        this.status.set('error');
      },
    });
  }

  /** Own account: deactivation and deletion are refused by the backend (SELF_ACCOUNT_PROTECTED). */
  protected isSelf(user: User): boolean {
    return user.keycloakId === this.auth.userId;
  }

  protected fullName(user: User): string {
    return [user.firstName, user.lastName].filter(Boolean).join(' ') || user.username;
  }

  protected toggleStatus(user: UserProfileDetails): void {
    const enable = !user.enabled;
    this.confirm
      .confirm({
        title: enable ? 'Activate account' : 'Deactivate account',
        message: enable
          ? `${user.username} will be able to sign in again.`
          : `${user.username} will not be able to sign in until the account is activated again.`,
        confirmLabel: enable ? 'Activate' : 'Deactivate',
        danger: !enable,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.busy.set(true);
          return this.userApi.setEnabled(user.id, enable);
        }),
      )
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          // PATCH returns the plain profile; editability does not change with the status.
          this.user.set({ ...updated, editable: user.editable });
          this.notify.success(enable ? 'Account activated.' : 'Account deactivated.');
        },
        error: (error: unknown) => this.fail(error),
      });
  }

  protected delete(user: User): void {
    this.confirm
      .confirm({
        title: 'Delete user',
        message: `The account ${user.username} will be removed from Keycloak together with its profile. This cannot be undone.`,
        confirmLabel: 'Delete',
        danger: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.busy.set(true);
          return this.userApi.delete(user.id);
        }),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notify.success(`User ${user.username} deleted.`);
          void this.router.navigateByUrl('/users');
        },
        error: (error: unknown) => this.fail(error),
      });
  }

  private loadRoles(keycloakId: string): void {
    this.rolesError.set('');
    this.roleApi.getUserRoles(keycloakId).subscribe({
      next: (roles) => this.roles.set(roles),
      error: (error: unknown) => this.rolesError.set(toApiError(error).message),
    });
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    this.notify.error(toApiError(error).message);
  }
}
