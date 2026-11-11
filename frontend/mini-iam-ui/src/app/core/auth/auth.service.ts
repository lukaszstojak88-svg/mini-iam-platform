import { Injectable, inject } from '@angular/core';
import Keycloak from 'keycloak-js';

import { AppRole, isAppRole } from './app-role';

/**
 * Thin facade over keycloak-js for UI purposes (display name, menu visibility, logout).
 * Roles are read from the access token; the ID token is never used to call the API (spec §5.2).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = inject(Keycloak);

  get authenticated(): boolean {
    return this.keycloak.authenticated ?? false;
  }

  /** Keycloak user id (`sub`) of the signed-in user. */
  get userId(): string {
    return this.keycloak.tokenParsed?.sub ?? '';
  }

  get username(): string {
    return (this.keycloak.tokenParsed?.['preferred_username'] as string | undefined) ?? '';
  }

  get roles(): AppRole[] {
    return (this.keycloak.realmAccess?.roles ?? []).filter(isAppRole);
  }

  hasAnyRole(roles: readonly AppRole[]): boolean {
    const granted = this.roles;
    return roles.some((role) => granted.includes(role));
  }

  login(redirectUri: string = window.location.href): Promise<void> {
    return this.keycloak.login({ redirectUri });
  }

  logout(): Promise<void> {
    return this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
