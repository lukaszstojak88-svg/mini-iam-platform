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

  it('turns URL filters into API params with a UTC [from, to + 1 s) range and shows them in the form', async () => {
    const harness = await RouterTestingHarness.create(
      '/audit-logs?eventType=ROLE_ASSIGNED&actor=kc-1&from=2026-11-01T08:30:00&to=2026-11-02T17:45:10',
    );
    const root = harness.routeNativeElement!;

    expect(list).toHaveBeenLastCalledWith(
      expect.objectContaining({
        eventType: 'ROLE_ASSIGNED',
        actorUserId: 'kc-1',
        from: new Date(2026, 10, 1, 8, 30, 0).toISOString(),
        to: new Date(2026, 10, 2, 17, 45, 11).toISOString(),
      }),
    );
    expect((byTestId(root, 'filter-from') as HTMLInputElement).value).toBe('01.11.2026 08:30:00');
    expect((byTestId(root, 'filter-to') as HTMLInputElement).value).toBe('02.11.2026 17:45:10');
  });

  it('shows times as dd.MM.yyyy HH:mm:ss', async () => {
    list.mockReturnValue(of(page([{ ...EVENT, occurredAt: new Date(2026, 10, 12, 8, 5, 9).toISOString() }])));
    const harness = await RouterTestingHarness.create('/audit-logs');

    expect(byTestId(harness.routeNativeElement!, 'event-row')?.textContent).toContain('12.11.2026 08:05:09');
  });

  it('takes typed dates; a day alone for "to" ends at 23:59:59', async () => {
    const harness = await RouterTestingHarness.create('/audit-logs');
    const root = harness.routeNativeElement!;
    const to = byTestId(root, 'filter-to') as HTMLInputElement;
    const typeTo = (value: string) => {
      to.value = value;
      to.dispatchEvent(new Event('input'));
      to.dispatchEvent(new Event('blur'));
    };

    typeTo('5.11.2026');
    byTestId(root, 'apply-filters')!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toContain('to=2026-11-05T23:59:59');
    expect(to.value).toBe('05.11.2026 23:59:59');

    typeTo('05.11.2026 10:15');
    byTestId(root, 'apply-filters')!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toContain('to=2026-11-05T10:15:00');
  });

  it('blocks filtering with an unparseable date', async () => {
    const harness = await RouterTestingHarness.create('/audit-logs');
    const root = harness.routeNativeElement!;
    const from = byTestId(root, 'filter-from') as HTMLInputElement;
    list.mockClear();

    from.value = '2026-11-05';
    from.dispatchEvent(new Event('input'));
    from.dispatchEvent(new Event('blur'));
    byTestId(root, 'apply-filters')!.click();
    harness.detectChanges();

    expect(byTestId(root, 'date-error')).not.toBeNull();
    expect(list).not.toHaveBeenCalled();
  });

  it('rejects a range that ends before it starts', async () => {
    const harness = await RouterTestingHarness.create('/audit-logs?from=2026-11-05T00:00:00&to=2026-11-01T00:00:00');
    const root = harness.routeNativeElement!;
    list.mockClear();

    byTestId(root, 'apply-filters')!.click();
    harness.detectChanges();

    expect(byTestId(root, 'range-error')).not.toBeNull();
    expect(list).not.toHaveBeenCalled();
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
