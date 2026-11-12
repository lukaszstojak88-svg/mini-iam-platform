import { HttpErrorResponse } from '@angular/common/http';
import { vi } from 'vitest';

import { PageResponse, User, UserDetails } from '../app/core/api/api-models';
import { AppRole } from '../app/core/auth/app-role';
import { AuthService } from '../app/core/auth/auth.service';

/** AuthService stub for a signed-in user with the given realm roles. */
export function authAs(roles: AppRole[], userId = 'kc-me'): Partial<AuthService> {
  return {
    authenticated: true,
    userId,
    username: 'me',
    roles,
    hasAnyRole: (required: readonly AppRole[]) => required.some((role) => roles.includes(role)),
  } as Partial<AuthService>;
}

export function notifyStub() {
  return { success: vi.fn(), error: vi.fn() };
}

export function user(overrides: Partial<User> = {}): User {
  return {
    id: 'id-1',
    keycloakId: 'kc-1',
    username: 'jan',
    email: 'jan@example.com',
    firstName: 'Jan',
    lastName: 'Kowalski',
    enabled: true,
    createdAt: '2026-11-10T09:00:00Z',
    updatedAt: '2026-11-10T09:00:00Z',
    ...overrides,
  };
}

export function userDetails(overrides: Partial<UserDetails> = {}): UserDetails {
  return { ...user(), editable: true, ...overrides };
}

export function page<T>(content: T[], total = content.length): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: total, totalPages: Math.ceil(total / 20) };
}

/** Problem detail error as returned by the services. */
export function problem(status: number, code?: string, extra: object = {}): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: { status, ...(code ? { code } : {}), ...extra } });
}

export const byTestId = (root: HTMLElement, testId: string) =>
  root.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
