import { ParamMap } from '@angular/router';

import { PAGE_SIZES } from '../core/api/api-models';

export interface SortState {
  active: string;
  direction: 'asc' | 'desc';
}

/** Reads a page number from query params (0-based, non-negative). */
export function readPage(params: ParamMap): number {
  const page = Number(params.get('page'));
  return Number.isInteger(page) && page > 0 ? page : 0;
}

/** Reads a page size from query params; only sizes offered by the UI are accepted (backend limit is 100). */
export function readSize(params: ParamMap, fallback = 20): number {
  const size = Number(params.get('size'));
  return (PAGE_SIZES as readonly number[]).includes(size) ? size : fallback;
}

/** Reads `sort=field,dir` restricted to the given fields. */
export function readSort(params: ParamMap, allowed: readonly string[], fallback: SortState): SortState {
  const [active, direction] = (params.get('sort') ?? '').split(',');
  if (allowed.includes(active) && (direction === 'asc' || direction === 'desc')) {
    return { active, direction };
  }
  return fallback;
}

export function sortParam(sort: SortState): string {
  return `${sort.active},${sort.direction}`;
}
