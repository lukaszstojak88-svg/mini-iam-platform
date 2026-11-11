import { AppRole } from '../auth/app-role';

/** Page of results returned by user-service and audit-service lists. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Page sizes offered by the UI; the backend rejects anything above 100 (spec §6.1). */
export const PAGE_SIZES = [10, 20, 50, 100] as const;

// ------------------------------------------------------------------ user-service

/** User profile. `id` is the local profile id, `keycloakId` identifies the account for auth/audit APIs. */
export interface User {
  id: string;
  keycloakId: string;
  username: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  temporaryPassword: string;
}

export interface UpdateProfileRequest {
  email: string;
  firstName: string | null;
  lastName: string | null;
}

/** Sortable fields of `GET /api/users` (server whitelist). */
export const USER_SORT_FIELDS = ['username', 'email', 'firstName', 'lastName', 'enabled', 'createdAt', 'updatedAt'] as const;
export type UserSortField = (typeof USER_SORT_FIELDS)[number];

export interface UserQuery {
  page: number;
  size: number;
  sort?: string;
  search?: string;
}

// ------------------------------------------------------------------ auth-service

export interface Role {
  name: AppRole;
  description: string | null;
  assignable: boolean;
}

/** Effective application roles of a user (`userId` = keycloakId). */
export interface UserRoles {
  userId: string;
  roles: AppRole[];
}

// ------------------------------------------------------------------ audit-service

export const AUDIT_EVENT_TYPES = [
  'USER_CREATED',
  'USER_UPDATED',
  'USER_ACTIVATED',
  'USER_DEACTIVATED',
  'USER_DELETED',
  'ROLE_ASSIGNED',
  'ROLE_REMOVED',
] as const;
export type AuditEventType = (typeof AUDIT_EVENT_TYPES)[number];

export interface AuditEvent {
  id: string;
  eventType: AuditEventType;
  actorUserId: string;
  /** Username at the time of the event; null for events recorded before usernames were stored. */
  actorUsername: string | null;
  targetUserId: string | null;
  targetUsername: string | null;
  details: Record<string, unknown>;
  occurredAt: string;
  correlationId: string | null;
}

export interface AuditQuery {
  page: number;
  size: number;
  sort?: string;
  eventType?: AuditEventType;
  actorUserId?: string;
  targetUserId?: string;
  /** Inclusive lower bound, ISO-8601 UTC. */
  from?: string;
  /** Exclusive upper bound, ISO-8601 UTC. */
  to?: string;
}
