import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { AuditEvent } from '../../core/api/api-models';
import {
  actorName,
  fromLocalDateTime,
  readFilters,
  shortId,
  targetName,
  toApiRange,
  toLocalDateTime,
} from './audit-filters';

describe('audit filters', () => {
  it('reads valid filters from query params and ignores invalid ones', () => {
    const filters = readFilters(
      convertToParamMap({ eventType: 'USER_DELETED', actor: 'kc-1', from: '2026-11-01T08:30:15', to: 'yesterday' }),
    );

    expect(filters).toEqual({
      eventType: 'USER_DELETED',
      actorUserId: 'kc-1',
      targetUserId: '',
      from: '2026-11-01T08:30:15',
      to: null,
    });
    expect(readFilters(convertToParamMap({ eventType: 'PASSWORD_CHANGED' })).eventType).toBeNull();
    expect(readFilters(convertToParamMap({ from: '2026-02-31T00:00:00' })).from).toBeNull();
  });

  it('expands plain days from older links to the whole day', () => {
    const filters = readFilters(convertToParamMap({ from: '2026-11-01', to: '2026-11-03' }));

    expect(filters.from).toBe('2026-11-01T00:00:00');
    expect(filters.to).toBe('2026-11-03T23:59:59');
  });

  it('turns an inclusive local range into an API range [from, to + 1 s) in UTC', () => {
    const range = toApiRange({
      eventType: null,
      actorUserId: '',
      targetUserId: '',
      from: '2026-11-01T08:30:00',
      to: '2026-11-03T23:59:59',
    });

    expect(range.from).toBe(new Date(2026, 10, 1, 8, 30, 0).toISOString());
    expect(range.to).toBe(new Date(2026, 10, 4, 0, 0, 0).toISOString());
    expect(toApiRange({ eventType: null, actorUserId: '', targetUserId: '', from: null, to: null })).toEqual({
      from: undefined,
      to: undefined,
    });
  });

  it('converts local date-times to and from the URL form, and shortens ids', () => {
    expect(toLocalDateTime(new Date(2026, 0, 5, 7, 4, 9))).toBe('2026-01-05T07:04:09');
    expect(toLocalDateTime(null)).toBeNull();
    expect(fromLocalDateTime('2026-01-05T07:04:09')).toEqual(new Date(2026, 0, 5, 7, 4, 9));
    expect(fromLocalDateTime('2026-01-05T25:00:00')).toBeNull();
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
