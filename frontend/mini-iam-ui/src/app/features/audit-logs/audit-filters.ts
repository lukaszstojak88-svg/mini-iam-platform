import { ParamMap } from '@angular/router';

import { AUDIT_EVENT_TYPES, AuditEvent, AuditEventType } from '../../core/api/api-models';

/**
 * Filters of the audit view as kept in the URL: `from` / `to` as local date-times `YYYY-MM-DDTHH:mm:ss`,
 * both inclusive (to the second).
 */
export interface AuditFilters {
  eventType: AuditEventType | null;
  actorUserId: string;
  targetUserId: string;
  from: string | null;
  to: string | null;
}

const DAY = /^\d{4}-\d{2}-\d{2}$/;
const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})$/;

export function readFilters(params: ParamMap): AuditFilters {
  const type = params.get('eventType');
  return {
    eventType: (AUDIT_EVENT_TYPES as readonly string[]).includes(type ?? '') ? (type as AuditEventType) : null,
    actorUserId: (params.get('actor') ?? '').slice(0, 64),
    targetUserId: (params.get('target') ?? '').slice(0, 64),
    from: readDateTime(params.get('from'), '00:00:00'),
    to: readDateTime(params.get('to'), '23:59:59'),
  };
}

/** Accepts `YYYY-MM-DDTHH:mm:ss` and, for older links, a plain day (expanded with `dayTime`). */
function readDateTime(value: string | null, dayTime: string): string | null {
  if (!value) {
    return null;
  }
  const normalized = DAY.test(value) ? `${value}T${dayTime}` : value;
  return fromLocalDateTime(normalized) ? normalized : null;
}

/** Local date-time of a picker value as `YYYY-MM-DDTHH:mm:ss` (seconds precision). */
export function toLocalDateTime(date: Date | null): string | null {
  if (!date) {
    return null;
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

/** Parses `YYYY-MM-DDTHH:mm:ss` as local time; null when malformed or not a real calendar date/time. */
export function fromLocalDateTime(value: string | null): Date | null {
  const match = value ? LOCAL_DATE_TIME.exec(value) : null;
  if (!match) {
    return null;
  }
  const [y, mo, d, h, mi, s] = match.slice(1).map(Number);
  const date = new Date(y, mo - 1, d, h, mi, s);
  const exact =
    date.getFullYear() === y && date.getMonth() === mo - 1 && date.getDate() === d &&
    date.getHours() === h && date.getMinutes() === mi && date.getSeconds() === s;
  return exact ? date : null;
}

/**
 * API range in the browser's time zone, as ISO-8601 UTC: `from` = the selected start (inclusive) and
 * `to` = the selected end + 1 s, because the API treats `to` as exclusive while the UI shows it inclusive
 * (events have sub-second timestamps, so 23:59:59.9 is still included).
 */
export function toApiRange(filters: AuditFilters): { from?: string; to?: string } {
  const start = fromLocalDateTime(filters.from);
  const end = fromLocalDateTime(filters.to);
  if (end) {
    end.setSeconds(end.getSeconds() + 1);
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
