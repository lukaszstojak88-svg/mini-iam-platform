import { Injectable } from '@angular/core';
import { MAT_NATIVE_DATE_FORMATS, MatDateFormats, NativeDateAdapter } from '@angular/material/core';

/** Date part shown and typed in date-time fields. */
export const DATE_FORMAT = 'dd.MM.yyyy';
/** Time part shown and typed in date-time fields (24-hour clock). */
export const TIME_FORMAT = 'HH:mm:ss';
/** Date and time in one field; also a valid Angular `DatePipe` pattern (tables, details). */
export const DATE_TIME_FORMAT = `${DATE_FORMAT} ${TIME_FORMAT}`;
/** Alias used with `DatePipe` in templates. */
export const DATE_TIME_PIPE_FORMAT = DATE_TIME_FORMAT;

/** Material date formats: dd.MM.yyyy and HH:mm:ss; calendar headers stay localized. */
export const DATE_TIME_FORMATS: MatDateFormats = {
  parse: { dateInput: DATE_FORMAT, timeInput: TIME_FORMAT },
  display: {
    ...MAT_NATIVE_DATE_FORMATS.display,
    dateInput: DATE_FORMAT,
    timeInput: TIME_FORMAT,
    timeOptionLabel: 'HH:mm',
  },
};

/** Hours, minutes, seconds. */
export type TimeOfDay = [number, number, number];

const DATE_PATTERN = /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/;
const TIME_PATTERN = /^(\d{1,2}):(\d{2})(?::(\d{2}))?$/;

const pad = (n: number) => String(n).padStart(2, '0');

/**
 * Native date adapter with fixed `dd.MM.yyyy` / `HH:mm:ss` formats for typing and display (the native adapter
 * parses with `Date.parse`, which does not understand `dd.MM.yyyy`). Weeks start on Monday.
 */
@Injectable()
export class DateTimeAdapter extends NativeDateAdapter {
  override format(date: Date, displayFormat: object | string): string {
    const day = `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()}`;
    const minutes = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    switch (displayFormat) {
      case DATE_FORMAT:
        return day;
      case 'HH:mm':
        return minutes;
      case TIME_FORMAT:
        return `${minutes}:${pad(date.getSeconds())}`;
      case DATE_TIME_FORMAT:
        return `${day} ${minutes}:${pad(date.getSeconds())}`;
      default:
        return super.format(date, displayFormat as object);
    }
  }

  /** Parses `d.M.yyyy` / `dd.MM.yyyy`; anything else (including 31.02.2026) is an invalid date. */
  override parse(value: unknown): Date | null {
    if (value == null || value === '') {
      return null;
    }
    if (value instanceof Date) {
      return value;
    }
    return parseDay(String(value).trim()) ?? this.invalid();
  }

  /** Parses `H:mm` / `HH:mm:ss` into today's date with that time; anything else is an invalid date. */
  override parseTime(value: unknown): Date | null {
    if (value == null || value === '') {
      return null;
    }
    if (value instanceof Date) {
      return value;
    }
    const time = parseTimeOfDay(String(value).trim());
    return time ? this.setTime(this.today(), ...time) : this.invalid();
  }

  /**
   * Parses `dd.MM.yyyy HH:mm:ss` (seconds optional). A date without a time gets `defaultTime`. Empty text gives
   * null; anything unparseable gives an invalid date.
   */
  parseDateTime(value: string, defaultTime: TimeOfDay = [0, 0, 0]): Date | null {
    const text = value.trim();
    if (!text) {
      return null;
    }
    const [dayPart, timePart, ...rest] = text.split(/\s+/);
    const day = parseDay(dayPart);
    const time = timePart === undefined ? defaultTime : parseTimeOfDay(timePart);
    if (!day || !time || rest.length > 0) {
      return this.invalid();
    }
    return this.setTime(day, ...time);
  }

  override getFirstDayOfWeek(): number {
    return 1;
  }
}

/** `d.M.yyyy` as a local midnight date, or null when malformed or not a real calendar day. */
function parseDay(text: string): Date | null {
  const match = DATE_PATTERN.exec(text);
  if (!match) {
    return null;
  }
  const [day, month, year] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(year, month - 1, day);
  const exists = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
  return exists ? date : null;
}

/** `H:mm` or `HH:mm:ss`, or null when malformed or out of range. */
export function parseTimeOfDay(text: string): TimeOfDay | null {
  const match = TIME_PATTERN.exec(text);
  if (!match) {
    return null;
  }
  const time: TimeOfDay = [Number(match[1]), Number(match[2]), Number(match[3] ?? 0)];
  return time[0] <= 23 && time[1] <= 59 && time[2] <= 59 ? time : null;
}
