import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { AuditEvent } from '../../core/api/api-models';
import { actorName, readFilters, shortId, targetName, toApiRange, toDay } from './audit-filters';

describe('audit filters', () => {
  it('reads valid filters from query params and ignores invalid ones', () => {
    const filters = readFilters(
      convertToParamMap({ eventType: 'USER_DELETED', actor: 'kc-1', from: '2026-11-01', to: 'yesterday' }),
    );

    expect(filters).toEqual({
      eventType: 'USER_DELETED',
      actorUserId: 'kc-1',
      targetUserId: '',
      fromDay: '2026-11-01',
      toDay: null,
    });
    expect(readFilters(convertToParamMap({ eventType: 'PASSWORD_CHANGED' })).eventType).toBeNull();
  });

  it('turns inclusive local days into an API range [from, to) in UTC', () => {
    const range = toApiRange({ eventType: null, actorUserId: '', targetUserId: '', fromDay: '2026-11-01', toDay: '2026-11-03' });

    // Local midnight of the first day and of the day AFTER the last one, whatever the time zone.
    expect(range.from).toBe(new Date(2026, 10, 1).toISOString());
    expect(range.to).toBe(new Date(2026, 10, 4).toISOString());
    expect(toApiRange({ eventType: null, actorUserId: '', targetUserId: '', fromDay: null, toDay: null })).toEqual({
      from: undefined,
      to: undefined,
    });
  });

  it('formats days and shortens ids', () => {
    expect(toDay(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(toDay(null)).toBeNull();
    expect(shortId('39297f43-ea55-4976-83aa-68bf1c1cb408')).toBe('39297f43…');
    expect(shortId(null)).toBe('—');
  });

  it('names actor and target from stored usernames, falling back to details.username', () => {
    const event: AuditEvent = {
      id: 'ev-1',
      eventType: 'USER_CREATED',
      actorUserId: 'kc-admin',
      actorUsername: 'admin',
      targetUserId: 'kc-new',
      targetUsername: 'tomasz',
      details: { username: 'tomasz' },
      occurredAt: '2026-11-10T09:00:00Z',
      correlationId: null,
    };

    expect(actorName(event)).toBe('admin');
    expect(targetName(event)).toBe('tomasz');
    const older = { ...event, actorUsername: null, targetUsername: null };
    expect(actorName(older)).toBeNull();
    expect(targetName(older)).toBe('tomasz');
    expect(targetName({ ...older, details: {} })).toBeNull();
  });
});
