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
        <h1>Brak uprawnień</h1>
        <p>Nie masz uprawnień do tego zasobu. Jeśli uważasz, że to błąd, skontaktuj się z administratorem.</p>
      </mat-card-content>
      <mat-card-actions>
        <a mat-flat-button routerLink="/dashboard">Wróć do dashboardu</a>
      </mat-card-actions>
    </mat-card>
  `,
  styles: `
    .denied {
      max-width: 560px;
      margin: 48px auto;
      text-align: center;
    }
    .icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: #dc2626;
    }
    mat-card-actions {
      justify-content: center;
    }
  `,
})
export class AccessDenied {}
