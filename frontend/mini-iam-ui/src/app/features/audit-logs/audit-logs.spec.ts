import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { byTestId, page, problem } from '../../../testing/test-helpers';
import { AuditEvent } from '../../core/api/api-models';
import { AuditApiService } from '../../core/api/audit-api.service';
import { AuditLogs } from './audit-logs';

const EVENT: AuditEvent = {
  id: 'ev-1',
  eventType: 'USER_DELETED',
  actorUserId: '39297f43-ea55-4976-83aa-68bf1c1cb408',
  actorUsername: 'admin',
  targetUserId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  targetUsername: 'jan',
  details: { username: 'jan' },
  occurredAt: '2026-11-10T09:00:00Z',
  correlationId: 'corr-1',
};

describe('AuditLogs', () => {
  const list = vi.fn();
  const dialog = { open: vi.fn() };

  beforeEach(() => {
    list.mockReset();
    dialog.open.mockReset();
    list.mockReturnValue(of(page([EVENT])));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'audit-logs', component: AuditLogs }]),
        { provide: AuditApiService, useValue: { list } },
        { provide: MatDialog, useValue: dialog },
      ],
    });
  });

  it('shows actor and target by username instead of the Keycloak id', async () => {
    const harness = await RouterTestingHarness.create('/audit-logs');
    const row = byTestId(harness.routeNativeElement!, 'event-row')!;

    expect(row.textContent).toContain('USER_DELETED');
    expect(byTestId(row, 'actor-name')?.textContent?.trim()).toBe('admin');
    expect(byTestId(row, 'target-name')?.textContent?.trim()).toBe('jan');
    expect(row.textContent).not.toContain('39297f43…');
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 20, sort: 'occurredAt,desc' }));
  });

  it('falls back to the shortened id for older events without usernames', async () => {
    list.mockReturnValue(
      of(page([{ ...EVENT, eventType: 'ROLE_ASSIGNED', actorUsername: null, targetUsername: null, details: {} }])),
    );
    const harness = await RouterTestingHarness.create('/audit-logs');
    const row = byTestId(harness.routeNativeElement!, 'event-row')!;

    expect(row.textContent).toContain('39297f43…');
    expect(row.textContent).toContain('aaaaaaaa…');
    expect(byTestId(row, 'actor-name')).toBeNull();
  });

  it('turns URL filters into API params with a UTC [from, to) range', async () => {
    await RouterTestingHarness.create('/audit-logs?eventType=ROLE_ASSIGNED&actor=kc-1&from=2026-11-01&to=2026-11-02');

    expect(list).toHaveBeenLastCalledWith(
      expect.objectContaining({
        eventType: 'ROLE_ASSIGNED',
        actorUserId: 'kc-1',
        from: new Date(2026, 10, 1).toISOString(),
        to: new Date(2026, 10, 3).toISOString(),
      }),
    );
  });

  it('applies filters from the form to the URL', async () => {
    const harness = await RouterTestingHarness.create('/audit-logs?page=2');
    const root = harness.routeNativeElement!;
    const target = byTestId(root, 'filter-target') as HTMLInputElement;
    target.value = 'kc-9';
    target.dispatchEvent(new Event('input'));
    byTestId(root, 'apply-filters')!.click();
    await harness.fixture.whenStable();

    const url = TestBed.inject(Router).url;
    expect(url).toContain('target=kc-9');
    expect(url).not.toContain('page=2');
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ targetUserId: 'kc-9', page: 0 }));
  });

  it('opens the details dialog', async () => {
    const harness = await RouterTestingHarness.create('/audit-logs');
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[data-testid="event-row"] button')!.click();

    expect(dialog.open).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ data: EVENT }));
  });

  it('shows the empty state', async () => {
    list.mockReturnValue(of(page([])));
    const harness = await RouterTestingHarness.create('/audit-logs');

    expect(byTestId(harness.routeNativeElement!, 'empty')).not.toBeNull();
  });

  it('shows the error state', async () => {
    list.mockReturnValue(throwError(() => problem(400, 'INVALID_REQUEST')));
    const harness = await RouterTestingHarness.create('/audit-logs?eventType=USER_CREATED');

    expect(byTestId(harness.routeNativeElement!, 'error')?.textContent).toContain('not accepted');
  });
});
