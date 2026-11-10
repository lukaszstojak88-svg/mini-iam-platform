/** Application roles (spec §2). SERVICE is a technical role and never used by the UI. */
export const APP_ROLES = ['ADMIN', 'MANAGER', 'USER', 'AUDITOR'] as const;

export type AppRole = (typeof APP_ROLES)[number];

export function isAppRole(value: string): value is AppRole {
  return (APP_ROLES as readonly string[]).includes(value);
}
