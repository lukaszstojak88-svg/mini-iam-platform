import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Statistic tile: icon in a circle, upper-case label and a large value (spec §10.3). */
@Component({
  selector: 'app-stat-tile',
  imports: [MatIconModule],
  template: `
    <div class="tile">
      <span class="icon"><mat-icon>{{ icon() }}</mat-icon></span>
      <span class="label">{{ label() }}</span>
      <span class="value">{{ value() }}</span>
    </div>
  `,
  styles: `
    .tile {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      grid-template-rows: auto auto;
      column-gap: 16px;
      align-items: center;
      padding: 16px 20px;
      background: var(--app-surface);
      border: 1px solid var(--app-border);
      border-radius: 12px;
    }
    .icon {
      grid-row: span 2;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: var(--app-primary-soft);
      color: var(--app-primary);
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
  `,
})
export class StatTile {
  readonly icon = input.required<string>();
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
}
