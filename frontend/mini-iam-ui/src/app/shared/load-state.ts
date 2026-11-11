import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

export type LoadStatus = 'loading' | 'loaded' | 'error';

/**
 * Loading and error states of a view (spec §10.2): a spinner while loading, an error message with "Try again"
 * on failure, nothing once loaded (the view renders its content).
 */
@Component({
  selector: 'app-load-state',
  imports: [MatProgressSpinnerModule, MatButtonModule, MatIconModule],
  template: `
    @switch (status()) {
      @case ('loading') {
        <div class="center" data-testid="loading" aria-live="polite">
          <mat-progress-spinner mode="indeterminate" diameter="40" aria-label="Loading" />
        </div>
      }
      @case ('error') {
        <div class="error card" role="alert" data-testid="error">
          <mat-icon>error_outline</mat-icon>
          <p>{{ message() }}</p>
          <button mat-stroked-button type="button" (click)="retry.emit()">Try again</button>
        </div>
      }
    }
  `,
  styles: `
    .center {
      display: flex;
      justify-content: center;
      padding: 48px;
    }
    .error {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      padding: 16px 20px;
      border-left: 4px solid var(--app-danger);
      color: var(--app-text);
    }
    .error mat-icon {
      color: var(--app-danger);
    }
    .error p {
      flex: 1;
      min-width: 0;
      margin: 0;
      overflow-wrap: anywhere;
    }
  `,
})
export class LoadState {
  readonly status = input.required<LoadStatus>();
  readonly message = input<string>('Something went wrong.');
  readonly retry = output<void>();
}
