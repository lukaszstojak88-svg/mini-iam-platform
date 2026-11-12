import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { DATE_FORMAT, DATE_TIME_FORMAT, DateTimeAdapter, TIME_FORMAT } from './date-time-adapter';

describe('DateTimeAdapter', () => {
  // NativeDateAdapter injects the locale, so it must be created in an injection context.
  const adapter = TestBed.runInInjectionContext(() => new DateTimeAdapter());

  it('formats dates as dd.MM.yyyy and times as HH:mm:ss', () => {
    const date = new Date(2026, 0, 5, 7, 4, 9);

    expect(adapter.format(date, DATE_FORMAT)).toBe('05.01.2026');
    expect(adapter.format(date, TIME_FORMAT)).toBe('07:04:09');
    expect(adapter.format(date, 'HH:mm')).toBe('07:04');
    expect(adapter.format(date, DATE_TIME_FORMAT)).toBe('05.01.2026 07:04:09');
  });

  it('parses date and time in one text; a day alone gets the default time', () => {
    expect(adapter.parseDateTime('16.11.2026 08:30:15')).toEqual(new Date(2026, 10, 16, 8, 30, 15));
    expect(adapter.parseDateTime(' 16.11.2026  8:30 ')).toEqual(new Date(2026, 10, 16, 8, 30, 0));
    expect(adapter.parseDateTime('16.11.2026', [23, 59, 59])).toEqual(new Date(2026, 10, 16, 23, 59, 59));
    expect(adapter.parseDateTime('')).toBeNull();
    expect(adapter.isValid(adapter.parseDateTime('16.11.2026 25:00')!)).toBe(false);
    expect(adapter.isValid(adapter.parseDateTime('16.11.2026 08:30 extra')!)).toBe(false);
    expect(adapter.isValid(adapter.parseDateTime('2026-11-16')!)).toBe(false);
  });

  it('parses dd.MM.yyyy and rejects other or impossible dates', () => {
    expect(adapter.parse('16.11.2026')).toEqual(new Date(2026, 10, 16));
    expect(adapter.parse('1.2.2026')).toEqual(new Date(2026, 1, 1));
    expect(adapter.parse('')).toBeNull();
    expect(adapter.isValid(adapter.parse('31.02.2026')!)).toBe(false);
    expect(adapter.isValid(adapter.parse('11/16/2026')!)).toBe(false);
  });

  it('parses HH:mm and HH:mm:ss and rejects out-of-range times', () => {
    const withSeconds = adapter.parseTime('08:30:15')!;
    expect([withSeconds.getHours(), withSeconds.getMinutes(), withSeconds.getSeconds()]).toEqual([8, 30, 15]);
    const withoutSeconds = adapter.parseTime('8:30')!;
    expect([withoutSeconds.getHours(), withoutSeconds.getMinutes(), withoutSeconds.getSeconds()]).toEqual([8, 30, 0]);
    expect(adapter.isValid(adapter.parseTime('24:00:00')!)).toBe(false);
    expect(adapter.isValid(adapter.parseTime('8 pm')!)).toBe(false);
  });

  it('starts weeks on Monday', () => {
    expect(adapter.getFirstDayOfWeek()).toBe(1);
  });
});
