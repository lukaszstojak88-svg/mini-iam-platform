import { Component, input } from '@angular/core';

export type PillVariant = 'success' | 'danger' | 'neutral';

/** Status "pill" with a coloured dot (spec §10.3): active - green, inactive - red, other - neutral. */
@Component({
  selector: 'app-status-pill',
  template: `<span class="pill" [class]="'pill ' + variant()"><span class="dot"></span><ng-content /></span>`,
  styles: `
    .pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 0.8rem;
      font-weight: 500;
      white-space: nowrap;
    }
    .dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: currentColor;
    }
    .success {
      color: var(--app-success);
      background: var(--app-success-bg);
    }
    .danger {
      color: var(--app-danger);
      background: var(--app-danger-bg);
    }
    .neutral {
      color: var(--app-text);
      background: var(--app-neutral-bg);
    }
  `,
})
export class StatusPill {
  readonly variant = input<PillVariant>('neutral');
}
