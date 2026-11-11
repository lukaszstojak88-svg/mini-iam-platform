import { DatePipe, JsonPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

import { AuditEvent } from '../../core/api/api-models';
import { StatusPill } from '../../shared/status-pill';
import { actorName, targetName } from './audit-filters';

/**
 * Read-only details of one audit event: actor and target by username (with the Keycloak id below), `details` as
 * key-value pairs and the correlation id.
 */
@Component({
  selector: 'app-audit-event-dialog',
  imports: [DatePipe, MatButtonModule, MatDialogModule, StatusPill],
  template: `
    <h2 mat-dialog-title>Audit event</h2>
    <mat-dialog-content class="details-card">
      <dl class="details">
        <dt>Type</dt>
        <dd><app-status-pill>{{ event.eventType }}</app-status-pill></dd>
        <dt>Occurred</dt>
        <dd>{{ event.occurredAt | date: 'medium' }} <span class="muted">({{ event.occurredAt }})</span></dd>
        <dt>Actor</dt>
        <dd data-testid="dialog-actor">
          @if (actor) {
            <div>{{ actor }}</div>
          }
          <div class="mono" [class.muted]="actor">{{ event.actorUserId }}</div>
        </dd>
        <dt>Target user</dt>
        <dd data-testid="dialog-target">
          @if (target) {
            <div>{{ target }}</div>
          }
          <div class="mono" [class.muted]="target">{{ event.targetUserId ?? '—' }}</div>
        </dd>
        @for (entry of entries; track entry[0]) {
          <dt>{{ entry[0] }}</dt>
          <dd>{{ format(entry[1]) }}</dd>
        }
        <dt>Correlation ID</dt>
        <dd class="mono">{{ event.correlationId ?? '—' }}</dd>
        <dt>Event ID</dt>
        <dd class="mono">{{ event.id }}</dd>
      </dl>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close cdkFocusInitial>Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .muted {
      color: var(--app-text-muted);
      font-size: 0.85rem;
    }
  `,
})
export class AuditEventDialog {
  protected readonly event = inject<AuditEvent>(MAT_DIALOG_DATA);
  protected readonly actor = actorName(this.event);
  protected readonly target = targetName(this.event);
  /** `details.username` is skipped when it is already shown as the target user. */
  protected readonly entries = Object.entries(this.event.details ?? {}).filter(
    ([key, value]) => !(key === 'username' && value === this.target),
  );
  private readonly json = new JsonPipe();

  protected format(value: unknown): string {
    if (Array.isArray(value)) {
      return value.join(', ');
    }
    return typeof value === 'object' && value !== null ? this.json.transform(value) : String(value);
  }
}
