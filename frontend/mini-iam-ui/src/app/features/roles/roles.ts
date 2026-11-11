import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, map, of, Subscription, switchMap } from 'rxjs';

import { Role, User } from '../../core/api/api-models';
import { RoleApiService } from '../../core/api/role-api.service';
import { UserApiService } from '../../core/api/user-api.service';
import { AppRole } from '../../core/auth/app-role';
import { AuthService } from '../../core/auth/auth.service';
import { toApiError } from '../../core/http/api-error';
import { ConfirmService } from '../../shared/confirm-dialog';
import { LoadState, LoadStatus } from '../../shared/load-state';
import { NotifyService } from '../../shared/notify.service';
import { PageHeader } from '../../shared/page-header';
import { StatTile } from '../../shared/stat-tile';

const TOKEN_REFRESH_NOTE = 'The user will see the change after their token is refreshed (up to 5 minutes).';

/**
 * Role management (ADMIN, spec §6.2): application roles of the realm and assigning or removing ADMIN, MANAGER and
 * AUDITOR for a chosen user. USER is the base role and cannot be removed; nobody changes their own roles. Keycloak
 * remains the source of truth - the view shows the roles returned by auth-service after every change.
 */
@Component({
  selector: 'app-roles',
  imports: [
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    LoadState,
    PageHeader,
    StatTile,
  ],
  templateUrl: './roles.html',
  styleUrl: './roles.scss',
})
export class Roles implements OnInit {
  private readonly roleApi = inject(RoleApiService);
  private readonly userApi = inject(UserApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly currentUserId = inject(AuthService).userId;

  protected readonly roleColumns = ['name', 'description', 'assignable'];

  protected readonly status = signal<LoadStatus>('loading');
  protected readonly errorMessage = signal('');
  protected readonly roles = signal<Role[]>([]);
  /** Roles the API marks as assignable (ADMIN, MANAGER, AUDITOR); USER is the base role. */
  private readonly assignableRoles = computed(() => this.roles().filter((role) => role.assignable).map((role) => role.name));
  protected readonly assignableCount = computed(() => this.assignableRoles().length);

  protected readonly userSearch = new FormControl<string | User>('', { nonNullable: true });
  protected readonly suggestions = signal<User[]>([]);
  protected readonly selected = signal<User | null>(null);
  protected readonly userRoles = signal<AppRole[] | null>(null);
  protected readonly userRolesError = signal('');
  protected readonly busy = signal(false);
  protected readonly roleToAssign = new FormControl<AppRole | null>(null);
  private userRolesRequest?: Subscription;

  /** Own account: role changes are refused by the backend (SELF_ROLE_CHANGE), so the UI disables them. */
  protected readonly isSelf = computed(() => this.selected()?.keycloakId === this.currentUserId);
  protected readonly missingRoles = computed(() => {
    const held = this.userRoles() ?? [];
    return this.assignableRoles().filter((role) => !held.includes(role));
  });

  ngOnInit(): void {
    this.loadRoles();

    this.userSearch.valueChanges
      .pipe(
        filter((value): value is string => typeof value === 'string'),
        debounceTime(300),
        map((value) => value.trim()),
        distinctUntilChanged(),
        switchMap((search) =>
          search.length < 2
            ? of([])
            : this.userApi.list({ page: 0, size: 10, sort: 'username,asc', search }).pipe(
                map((page) => page.content),
                catchError(() => of([])),
              ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((users) => this.suggestions.set(users));

    // Pre-selection from User Details: /roles?user=<local profile id>.
    const preselected = this.route.snapshot.queryParamMap.get('user');
    if (preselected) {
      this.userApi.get(preselected).subscribe({
        next: (user) => this.select(user),
        error: (error: unknown) => this.notify.error(toApiError(error).message),
      });
    }
  }

  protected loadRoles(): void {
    this.status.set('loading');
    this.roleApi.listRoles().subscribe({
      next: (roles) => {
        this.roles.set(roles);
        this.status.set('loaded');
      },
      error: (error: unknown) => {
        this.errorMessage.set(toApiError(error).message);
        this.status.set('error');
      },
    });
  }

  protected displayUser(user: User | string | null): string {
    return typeof user === 'object' && user ? user.username : (user ?? '');
  }

  protected onUserSelected(event: MatAutocompleteSelectedEvent): void {
    this.select(event.option.value as User);
  }

  protected canRemove(role: AppRole): boolean {
    return this.assignableRoles().includes(role) && !this.isSelf();
  }

  protected assign(): void {
    const user = this.selected();
    const role = this.roleToAssign.value;
    if (!user || !role || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.roleApi.assign(user.keycloakId, role).subscribe({
      next: (result) => {
        this.busy.set(false);
        // Another user may have been selected meanwhile - never show one user's roles under another.
        if (this.isSelected(user)) {
          this.userRoles.set(result.roles);
          this.roleToAssign.reset(null);
        }
        this.notify.success(`Role ${role} assigned to ${user.username}. ${TOKEN_REFRESH_NOTE}`);
      },
      error: (error: unknown) => this.fail(error),
    });
  }

  protected remove(role: AppRole): void {
    const user = this.selected();
    if (!user || this.busy()) {
      return;
    }
    this.confirm
      .confirm({
        title: 'Remove role',
        message: `Remove the role ${role} from ${user.username}?`,
        confirmLabel: 'Remove',
        danger: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.busy.set(true);
          return this.roleApi.remove(user.keycloakId, role);
        }),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          if (this.isSelected(user)) {
            this.userRoles.update((roles) => (roles ?? []).filter((held) => held !== role));
          }
          this.notify.success(`Role ${role} removed from ${user.username}. ${TOKEN_REFRESH_NOTE}`);
        },
        error: (error: unknown) => this.fail(error),
      });
  }

  protected retryUserRoles(): void {
    const user = this.selected();
    if (user) {
      this.select(user);
    }
  }

  private select(user: User): void {
    this.selected.set(user);
    this.userSearch.setValue(user, { emitEvent: false });
    this.userRoles.set(null);
    this.userRolesError.set('');
    this.roleToAssign.reset(null);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { user: user.id }, replaceUrl: true });
    // A slower response for a previously selected user must not overwrite this one.
    this.userRolesRequest?.unsubscribe();
    this.userRolesRequest = this.roleApi.getUserRoles(user.keycloakId).subscribe({
      next: (result) => this.userRoles.set(result.roles),
      error: (error: unknown) => this.userRolesError.set(toApiError(error).message),
    });
  }

  private isSelected(user: User): boolean {
    return this.selected()?.keycloakId === user.keycloakId;
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    this.notify.error(toApiError(error).message);
  }
}
