import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

/** Shown when the user lacks a role required by a route or the API returned 403 (spec §10.1). */
@Component({
  selector: 'app-access-denied',
  imports: [MatCardModule, MatButtonModule, MatIconModule, RouterLink],
  template: `
    <mat-card class="denied">
      <mat-card-content>
        <mat-icon class="icon">lock</mat-icon>
        <h1>Access denied</h1>
        <p>You do not have permission to view this page. If you think this is a mistake, contact the administrator.</p>
      </mat-card-content>
      <mat-card-actions>
        <a mat-flat-button routerLink="/dashboard">Back to dashboard</a>
      </mat-card-actions>
    </mat-card>
  `,
  styles: `
    .denied {
      max-width: 560px;
      margin: 48px auto;
      text-align: center;
      border: 1px solid var(--app-border);
      border-radius: 12px;
      box-shadow: none;
    }
    .icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: var(--app-danger);
    }
    mat-card-actions {
      justify-content: center;
    }
  `,
})
export class AccessDenied {}
