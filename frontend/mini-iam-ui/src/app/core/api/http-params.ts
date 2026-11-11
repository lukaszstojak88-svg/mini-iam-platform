import { HttpParams } from '@angular/common/http';

/** Builds query parameters, skipping undefined, null and blank values. */
export function toHttpParams(query: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || (typeof value === 'string' && value.trim() === '')) {
      continue;
    }
    params = params.set(key, typeof value === 'string' ? value.trim() : String(value));
  }
  return params;
}
