import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { Component, forwardRef, inject, input, signal } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormControl,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { DateAdapter, ErrorStateMatcher, MAT_DATE_FORMATS } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTimepickerModule } from '@angular/material/timepicker';

import {
  DATE_TIME_FORMAT,
  DATE_TIME_FORMATS,
  DateTimeAdapter,
  parseTimeOfDay,
  TimeOfDay,
} from './date-time-adapter';

const isValidDate = (value: unknown): value is Date => value instanceof Date && !isNaN(value.getTime());

/**
 * One form field for a date and time (`dd.MM.yyyy HH:mm:ss`) bound to a `FormControl<Date | null>`.
 * The text can be typed (parsed on blur / Enter; a day without a time gets `defaultTime`), or picked in a popup
 * with a calendar and a time field (typed, or chosen from a list every 30 min via the clock icon; Apply / Cancel). Unparseable text makes the control invalid (`dateTime`).
 */
@Component({
  selector: 'app-date-time-field',
  imports: [
    CdkConnectedOverlay,
    CdkOverlayOrigin,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTimepickerModule,
    ReactiveFormsModule,
  ],
  providers: [
    { provide: DateAdapter, useClass: DateTimeAdapter },
    { provide: MAT_DATE_FORMATS, useValue: DATE_TIME_FORMATS },
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DateTimeField), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => DateTimeField), multi: true },
  ],
  template: `
    <mat-form-field appearance="outline" subscriptSizing="dynamic" cdkOverlayOrigin #origin="cdkOverlayOrigin">
      <mat-label>{{ label() }}</mat-label>
      <input
        matInput
        [value]="text()"
        [disabled]="disabled()"
        [errorStateMatcher]="errorMatcher"
        placeholder="dd.MM.yyyy HH:mm:ss"
        autocomplete="off"
        [attr.data-testid]="testId()"
        (input)="text.set($any($event.target).value)"
        (blur)="commit()"
        (keydown.enter)="commit()"
      />
      <button
        mat-icon-button
        matIconSuffix
        type="button"
        [disabled]="disabled()"
        [attr.aria-label]="'Choose ' + label() + ' date and time'"
        [attr.data-testid]="testId() + '-toggle'"
        (click)="toggle()"
      >
        <mat-icon>calendar_month</mat-icon>
      </button>
    </mat-form-field>

    <ng-template
      cdkConnectedOverlay
      [cdkConnectedOverlayOrigin]="origin"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayHasBackdrop]="true"
      cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
      (backdropClick)="close()"
      (detach)="close()"
      (overlayKeydown)="$event.key === 'Escape' && close()"
    >
      <div class="popup" role="dialog" [attr.aria-label]="label() + ' date and time'">
        <mat-calendar [selected]="pickedDay()" [startAt]="pickedDay()" (selectedChange)="pickedDay.set($event)" />
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="time">
          <mat-label>Time</mat-label>
          <input
            matInput
            [matTimepicker]="timeList"
            [matTimepickerOpenOnClick]="false"
            [formControl]="pickedTime"
            placeholder="HH:mm:ss"
            autocomplete="off"
            [attr.data-testid]="testId() + '-time'"
            (keydown.enter)="apply()"
          />
          <mat-timepicker-toggle matIconSuffix [for]="timeList" [attr.data-testid]="testId() + '-time-toggle'" />
          <mat-timepicker #timeList aria-label="Times" />
        </mat-form-field>
        <div class="actions">
          <button mat-button type="button" (click)="close()">Cancel</button>
          <button
            mat-flat-button
            type="button"
            [disabled]="!canApply()"
            [attr.data-testid]="testId() + '-apply'"
            (click)="apply()"
          >
            Apply
          </button>
        </div>
      </div>
    </ng-template>
  `,
  styles: `
    :host {
      display: inline-block;
    }
    mat-form-field {
      width: 100%;
    }
    .popup {
      width: 300px;
      padding: 8px 12px 12px;
      background: var(--app-surface);
      border: 1px solid var(--app-border);
      border-radius: 12px;
      box-shadow: 0 8px 24px rgb(0 0 0 / 0.15);
    }
    .time {
      width: 100%;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 12px;
    }
  `,
})
export class DateTimeField implements ControlValueAccessor, Validator {
  readonly label = input.required<string>();
  /** Time used when only a day is typed and preset in the popup when the field is empty (`HH:mm:ss`). */
  readonly defaultTime = input('00:00:00');
  /** Base `data-testid`; the toggle, time and apply elements get suffixes. */
  readonly testId = input('date-time');

  private readonly adapter = inject(DateAdapter) as DateTimeAdapter;

  protected readonly text = signal('');
  protected readonly disabled = signal(false);
  protected readonly open = signal(false);
  protected readonly pickedDay = signal<Date | null>(null);
  /** Time chosen in the popup; only hours, minutes and seconds are used (an invalid date for unparseable text). */
  protected readonly pickedTime = new FormControl<Date | null>(null);

  /** Committed value: a valid date, null (empty) or an invalid date (text that could not be parsed). */
  private value: Date | null = null;
  private onChange: (value: Date | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  /** The inner input has no NgControl, so its error state follows the committed value. */
  protected readonly errorMatcher: ErrorStateMatcher = {
    isErrorState: () => this.value !== null && !isValidDate(this.value),
  };

  writeValue(value: Date | null): void {
    this.value = value ?? null;
    this.text.set(isValidDate(value) ? this.adapter.format(value, DATE_TIME_FORMAT) : '');
  }

  registerOnChange(fn: (value: Date | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value as unknown;
    return value != null && !isValidDate(value) ? { dateTime: true } : null;
  }

  /** Parses the typed text; a valid value is shown back in the canonical format. */
  protected commit(): void {
    const parsed = this.adapter.parseDateTime(this.text(), this.defaultTimeOfDay());
    if (isValidDate(parsed)) {
      this.text.set(this.adapter.format(parsed, DATE_TIME_FORMAT));
    }
    this.setValue(parsed);
    this.onTouched();
  }

  protected toggle(): void {
    if (this.open()) {
      this.close();
      return;
    }
    const current = isValidDate(this.value) ? this.value : null;
    this.pickedDay.set(current);
    this.pickedTime.setValue(current ?? this.adapter.setTime(this.adapter.today(), ...this.defaultTimeOfDay()));
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
  }

  protected apply(): void {
    const day = this.pickedDay();
    const time = this.pickedTime.value;
    if (!day || !isValidDate(time)) {
      return;
    }
    const value = new Date(
      day.getFullYear(), day.getMonth(), day.getDate(), time.getHours(), time.getMinutes(), time.getSeconds(),
    );
    this.text.set(this.adapter.format(value, DATE_TIME_FORMAT));
    this.setValue(value);
    this.onTouched();
    this.close();
  }

  protected canApply(): boolean {
    return !!this.pickedDay() && isValidDate(this.pickedTime.value);
  }

  private setValue(value: Date | null): void {
    const same =
      value === this.value ||
      (value !== null && this.value !== null && value.getTime() === this.value.getTime()) ||
      (value !== null && this.value !== null && !isValidDate(value) && !isValidDate(this.value));
    this.value = value;
    if (!same) {
      this.onChange(value);
    }
  }

  private defaultTimeOfDay(): TimeOfDay {
    return parseTimeOfDay(this.defaultTime()) ?? [0, 0, 0];
  }
}
