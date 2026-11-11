import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AppRole } from '../../core/auth/app-role';
import { AuthService } from '../../core/auth/auth.service';

interface MenuItem {
  path: string;
  label: string;
  icon: string;
  /** Visible to users having any of these roles; empty = everyone (spec §2.1, docs/frontend.md §4). */
  roles: readonly AppRole[];
}

const MENU: readonly MenuItem[] = [
  { path: '/dashboard', label: 'Dashboard', icon: 'dashboard', roles: [] },
  { path: '/users', label: 'Users', icon: 'group', roles: ['ADMIN', 'MANAGER'] },
  { path: '/roles', label: 'Roles', icon: 'verified_user', roles: ['ADMIN'] },
  { path: '/audit-logs', label: 'Audit log', icon: 'policy', roles: ['ADMIN', 'AUDITOR'] },
];

/**
 * Layout for authenticated views: side navigation filtered by the user's roles, top bar with the current user
 * and sign out. Hiding menu items is a convenience only - routes and the backend enforce permissions.
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  /** Re-evaluated on change detection, so the menu follows roles from a refreshed token. */
  protected get menu(): readonly MenuItem[] {
    return MENU.filter((item) => item.roles.length === 0 || this.auth.hasAnyRole(item.roles));
  }

  protected logout(): void {
    void this.auth.logout();
  }
}
