import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { Shell } from './layout/shell/shell';

const USER_MANAGERS = ['ADMIN', 'MANAGER'];

/**
 * Routes guarded by `authGuard`; optional `data.roles` restricts a route to any of the listed realm roles
 * (matrix in spec §2.1). Guards only control navigation - the backend always enforces permissions (spec §10.2).
 * All views are lazy-loaded; only the shell is part of the initial bundle.
 */
export const routes: Routes = [
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
        title: 'Dashboard · Mini IAM',
      },
      {
        path: 'users',
        loadComponent: () => import('./features/users/users').then((m) => m.Users),
        data: { roles: USER_MANAGERS },
        title: 'Users · Mini IAM',
      },
      {
        path: 'users/new',
        loadComponent: () => import('./features/user-form/user-form').then((m) => m.UserForm),
        data: { roles: USER_MANAGERS },
        title: 'New user · Mini IAM',
      },
      {
        path: 'users/:id',
        loadComponent: () => import('./features/user-details/user-details').then((m) => m.UserDetails),
        data: { roles: USER_MANAGERS },
        title: 'User details · Mini IAM',
      },
      {
        path: 'users/:id/edit',
        loadComponent: () => import('./features/user-form/user-form').then((m) => m.UserForm),
        data: { roles: USER_MANAGERS },
        title: 'Edit user · Mini IAM',
      },
      {
        path: 'roles',
        loadComponent: () => import('./features/roles/roles').then((m) => m.Roles),
        data: { roles: ['ADMIN'] },
        title: 'Roles · Mini IAM',
      },
      {
        path: 'audit-logs',
        loadComponent: () => import('./features/audit-logs/audit-logs').then((m) => m.AuditLogs),
        data: { roles: ['ADMIN', 'AUDITOR'] },
        title: 'Audit log · Mini IAM',
      },
      {
        path: 'access-denied',
        loadComponent: () => import('./features/access-denied/access-denied').then((m) => m.AccessDenied),
        title: 'Access denied · Mini IAM',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
