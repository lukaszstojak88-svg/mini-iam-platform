import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthApiService, CurrentUser } from '../../core/api/auth-api.service';

type LoadState = 'loading' | 'loaded' | 'error';

/** Basic information about the logged-in user, as seen by the backend (`GET /api/auth/me`). */
@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, MatChipsModule, MatProgressSpinnerModule, MatButtonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly authApi = inject(AuthApiService);

  protected readonly state = signal<LoadState>('loading');
  protected readonly user = signal<CurrentUser | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.authApi.getCurrentUser().subscribe({
      next: (user) => {
        this.user.set(user);
        this.state.set('loaded');
      },
      error: () => this.state.set('error'),
    });
  }
}
