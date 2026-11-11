import { AbstractControl, FormGroup, ValidationErrors } from '@angular/forms';

import { ApiError } from '../core/http/api-error';

/** Rejects values made of white space only (the backend uses @NotBlank); error key `blank`. */
export function notBlank(control: AbstractControl): ValidationErrors | null {
  const value = control.value as string | null;
  return value && value.trim().length === 0 ? { blank: true } : null;
}

/**
 * Shows server-side validation errors next to the form fields (error key `server`):
 * `VALIDATION_FAILED` field errors and `USERNAME_OR_EMAIL_TAKEN` (marked on username and e-mail).
 * Returns true when at least one field got an error.
 */
export function applyFieldErrors(form: FormGroup, error: ApiError): boolean {
  let applied = false;
  const mark = (control: AbstractControl | null, message: string) => {
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: message });
      control.markAsTouched();
      applied = true;
    }
  };
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    mark(form.get(field), `Rejected by the server: ${message}.`);
  }
  if (error.code === 'USERNAME_OR_EMAIL_TAKEN') {
    mark(form.get('username'), 'Username or e-mail is already in use.');
    mark(form.get('email'), 'Username or e-mail is already in use.');
  }
  return applied;
}
