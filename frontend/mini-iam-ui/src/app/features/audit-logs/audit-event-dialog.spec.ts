import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { describe, expect, it } from 'vitest';

import { byTestId } from '../../../testing/test-helpers';
import { AuditEvent } from '../../core/api/api-models';
import { AuditEventDialog } from './audit-event-dialog';

const EVENT: AuditEvent = {
  id: 'ev-1',
  eventType: 'USER_CREATED',
  actorUserId: '783679c2-af24-497c-a904-3c9964d62177',
  actorUsername: 'admin',
  targetUserId: '21bdc972-549e-4763-ad60-e6d39863a990',
  targetUsername: 'tomasz',
  details: { username: 'tomasz' },
  occurredAt: '2026-11-11T21:45:57Z',
  correlationId: 'corr-1',
};

function render(event: AuditEvent): HTMLElement {
  TestBed.configureTestingModule({ providers: [{ provide: MAT_DIALOG_DATA, useValue: event }] });
  const fixture = TestBed.createComponent(AuditEventDialog);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('AuditEventDialog', () => {
  it('shows actor and target usernames with the Keycloak ids and no duplicated username detail', () => {
    const el = render(EVENT);

    expect(byTestId(el, 'dialog-actor')?.textContent).toContain('admin');
    expect(byTestId(el, 'dialog-actor')?.textContent).toContain(EVENT.actorUserId);
    expect(byTestId(el, 'dialog-target')?.textContent).toContain('tomasz');
    expect(byTestId(el, 'dialog-target')?.textContent).toContain(EVENT.targetUserId!);
    const terms = Array.from(el.querySelectorAll('dt')).map((dt) => dt.textContent?.trim());
    expect(terms).not.toContain('username');
  });

  it('shows only the id for older events without an actor username', () => {
    const el = render({ ...EVENT, actorUsername: null });

    expect(byTestId(el, 'dialog-actor')?.textContent?.trim()).toBe(EVENT.actorUserId);
  });
});
