import { ParamMap } from '@angular/router';

import { AUDIT_EVENT_TYPES, AuditEvent, AuditEventType } from '../../core/api/api-models';

/** Filters of the audit view as kept in the URL: dates as local calendar days (YYYY-MM-DD), both inclusive. */
export interface AuditFilters {
  eventType: AuditEventType | null;
  actorUserId: string;
  targetUserId: string;
  fromDay: string | null;
  toDay: string | null;
}

const DAY = /^\d{4}-\d{2}-\d{2}$/;

export function readFilters(params: ParamMap): AuditFilters {
  const type = params.get('eventType');
  const day = (name: string) => {
    const value = params.get(name);
    return value && DAY.test(value) ? value : null;
  };
  return {
    eventType: (AUDIT_EVENT_TYPES as readonly string[]).includes(type ?? '') ? (type as AuditEventType) : null,
    actorUserId: (params.get('actor') ?? '').slice(0, 64),
    targetUserId: (params.get('target') ?? '').slice(0, 64),
    fromDay: day('from'),
    toDay: day('to'),
  };
}

/** Local calendar day of a date picker value as YYYY-MM-DD. */
export function toDay(date: Date | null): string | null {
  if (!date) {
    return null;
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function fromDay(day: string | null): Date | null {
  if (!day) {
    return null;
  }
  const [y, m, d] = day.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/**
 * API range for the selected days in the browser's time zone: `from` = start of the first day (inclusive),
 * `to` = start of the day after the last day (exclusive, as the API expects), both as ISO-8601 UTC.
 */
export function toApiRange(filters: AuditFilters): { from?: string; to?: string } {
  const start = fromDay(filters.fromDay);
  const end = fromDay(filters.toDay);
  if (end) {
    end.setDate(end.getDate() + 1);
  }
  return { from: start?.toISOString(), to: end?.toISOString() };
}

/** Actor username recorded in the event, or null for older events stored without it. */
export function actorName(event: AuditEvent): string | null {
  return event.actorUsername || null;
}

/**
 * Target username recorded in the event. Older events have none; for them `details.username`
 * (USER_CREATED, USER_DELETED) is used when present.
 */
export function targetName(event: AuditEvent): string | null {
  if (event.targetUsername) {
    return event.targetUsername;
  }
  const fromDetails = event.details?.['username'];
  return typeof fromDetails === 'string' && fromDetails ? fromDetails : null;
}

/** Short form of a Keycloak id for tables (the full id is shown in a tooltip). */
export function shortId(id: string | null): string {
  if (!id) {
    return '—';
  }
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}
