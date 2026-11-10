import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { AccessDenied } from './features/access-denied/access-denied';
import { Dashboard } from './features/dashboard/dashboard';
import { Shell } from './layout/shell/shell';

/**
 * Routes guarded by `authGuard`; optional `data.roles` restricts a route to any of the listed realm roles.
 * Guards only control navigation - the backend always enforces permissions (spec §10.2).
 */
export const routes: Routes = [
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard, title: 'Dashboard · Mini IAM' },
      { path: 'access-denied', component: AccessDenied, title: 'Access denied · Mini IAM' },
    ],
  },
  { path: '**', redirectTo: '' },
];
