import { Component, input } from '@angular/core';

/** Page title with a description on the left and action buttons (projected content) on the right. */
@Component({
  selector: 'app-page-header',
  template: `
    <header class="page-header">
      <div class="text">
        <h1>{{ title() }}</h1>
        @if (description()) {
          <p>{{ description() }}</p>
        }
      </div>
      <div class="actions"><ng-content /></div>
    </header>
  `,
  styles: `
    .page-header {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 24px;
    }
    .text {
      min-width: 0;
    }
    h1 {
      margin: 0 0 4px;
      font-size: 1.75rem;
      font-weight: 600;
    }
    p {
      margin: 0;
      color: var(--app-text-muted);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly description = input<string>('');
}
