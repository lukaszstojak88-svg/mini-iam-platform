import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { describe, expect, it } from 'vitest';

import { byTestId } from '../../testing/test-helpers';
import { DateTimeField } from './date-time-field';

@Component({
  imports: [ReactiveFormsModule, DateTimeField],
  template: `<app-date-time-field label="To" [formControl]="control" defaultTime="23:59:59" testId="dt" />`,
})
class Host {
  readonly control = new FormControl<Date | null>(null);
}

function render(initial: Date | null = null) {
  const fixture = TestBed.createComponent(Host);
  fixture.componentInstance.control.setValue(initial);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  const input = byTestId(root, 'dt') as HTMLInputElement;
  const type = (value: string) => {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  };
  return { fixture, control: fixture.componentInstance.control, input, type };
}

/** The popup is rendered in the CDK overlay container, outside the component. */
const overlay = () => document.querySelector<HTMLElement>('.cdk-overlay-container')!;

describe('DateTimeField', () => {
  it('shows the value as dd.MM.yyyy HH:mm:ss', () => {
    const { input } = render(new Date(2026, 10, 2, 17, 45, 10));

    expect(input.value).toBe('02.11.2026 17:45:10');
  });

  it('parses typed text; a day alone gets the default time', () => {
    const { control, input, type } = render();

    type('16.11.2026 08:30:15');
    expect(control.value).toEqual(new Date(2026, 10, 16, 8, 30, 15));

    type('16.11.2026');
    expect(control.value).toEqual(new Date(2026, 10, 16, 23, 59, 59));
    expect(input.value).toBe('16.11.2026 23:59:59');

    type('');
    expect(control.value).toBeNull();
    expect(control.valid).toBe(true);
  });

  it('marks unparseable text as invalid', () => {
    const { control, type } = render();

    type('16/11/2026');

    expect(control.hasError('dateTime')).toBe(true);
  });

  it('picks a day and a time in the popup and applies them', () => {
    const { fixture, control } = render(new Date(2026, 10, 2, 10, 0, 0));
    byTestId(fixture.nativeElement, 'dt-toggle')!.click();
    fixture.detectChanges();

    // Day 20 of the shown month (November 2026), then a new time.
    const cell = Array.from(overlay().querySelectorAll<HTMLElement>('.mat-calendar-body-cell')).find(
      (c) => c.textContent?.trim() === '20',
    )!;
    cell.click();
    const time = byTestId(overlay(), 'dt-time') as HTMLInputElement;
    expect(time.value).toBe('10:00:00');
    time.value = '18:05:30';
    time.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    byTestId(overlay(), 'dt-apply')!.click();
    fixture.detectChanges();

    expect(control.value).toEqual(new Date(2026, 10, 20, 18, 5, 30));
    expect(byTestId(overlay(), 'dt-time')).toBeNull();
  });

  it('chooses a time from the clock list', async () => {
    // jsdom has no scrollIntoView; the timepicker scrolls the selected option into view.
    Element.prototype.scrollIntoView ??= () => undefined;
    const { fixture, control } = render(new Date(2026, 10, 2, 10, 0, 0));
    byTestId(fixture.nativeElement, 'dt-toggle')!.click();
    fixture.detectChanges();

    byTestId(overlay(), 'dt-time-toggle')!.querySelector('button')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const option = Array.from(overlay().querySelectorAll<HTMLElement>('mat-option')).find(
      (o) => o.textContent?.trim() === '08:30',
    )!;
    option.click();
    fixture.detectChanges();
    expect((byTestId(overlay(), 'dt-time') as HTMLInputElement).value).toBe('08:30:00');
    byTestId(overlay(), 'dt-apply')!.click();
    fixture.detectChanges();

    expect(control.value).toEqual(new Date(2026, 10, 2, 8, 30, 0));
  });

  it('presets the default time in the popup and leaves the value unchanged on Cancel', () => {
    const { fixture, control } = render();
    byTestId(fixture.nativeElement, 'dt-toggle')!.click();
    fixture.detectChanges();

    expect((byTestId(overlay(), 'dt-time') as HTMLInputElement).value).toBe('23:59:59');
    expect((byTestId(overlay(), 'dt-apply') as HTMLButtonElement).disabled).toBe(true);
    Array.from(overlay().querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Cancel')!
      .click();
    fixture.detectChanges();

    expect(control.value).toBeNull();
    expect(byTestId(overlay(), 'dt-time')).toBeNull();
  });
});
