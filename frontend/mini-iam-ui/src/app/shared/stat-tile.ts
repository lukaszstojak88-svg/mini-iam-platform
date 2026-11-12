import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Statistic tile: icon in a circle, upper-case label and a large value with an optional hint in brackets (spec §10.3). */
@Component({
  selector: 'app-stat-tile',
  imports: [MatIconModule],
  template: `
    <div class="tile">
      <span class="icon"><mat-icon>{{ icon() }}</mat-icon></span>
      <span class="text">
        <span class="label">{{ label() }}</span>
        <span class="value">
          {{ value() }}
          @if (hint(); as text) {
            <span class="hint" data-testid="stat-hint">({{ text }})</span>
          }
        </span>
      </span>
    </div>
  `,
  styles: `
    .tile {
      display: flex;
      gap: 16px;
      align-items: center;
      padding: 16px 20px;
      background: var(--app-surface);
      border: 1px solid var(--app-border);
      border-radius: 12px;
    }
    .icon {
      flex: none;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: var(--app-primary-soft);
      color: var(--app-primary);
    }
    .text {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .label {
      font-size: 0.75rem;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--app-text-muted);
    }
    .value {
      font-size: 1.5rem;
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .hint {
      margin-left: 4px;
      font-size: 0.85rem;
      font-weight: 400;
      color: var(--app-text-muted);
      overflow-wrap: anywhere;
    }
  `,
})
export class StatTile {
  readonly icon = input.required<string>();
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly hint = input<string>();
}
