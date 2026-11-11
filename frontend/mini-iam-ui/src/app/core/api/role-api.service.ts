import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AppRole } from '../auth/app-role';
import { Role, UserRoles } from './api-models';

/** auth-service role API (spec §6.2). `keycloakId` is the Keycloak user id, not the local profile id. */
@Injectable({ providedIn: 'root' })
export class RoleApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.api.authService}/api`;

  listRoles(): Observable<Role[]> {
    return this.http.get<Role[]>(`${this.baseUrl}/roles`);
  }

  getUserRoles(keycloakId: string): Observable<UserRoles> {
    return this.http.get<UserRoles>(this.userRolesUrl(keycloakId));
  }

  /** Returns the user's roles after the change (200). */
  assign(keycloakId: string, roleName: AppRole): Observable<UserRoles> {
    return this.http.post<UserRoles>(this.userRolesUrl(keycloakId), { roleName });
  }

  remove(keycloakId: string, roleName: AppRole): Observable<void> {
    return this.http.delete<void>(`${this.userRolesUrl(keycloakId)}/${encodeURIComponent(roleName)}`);
  }

  private userRolesUrl(keycloakId: string): string {
    return `${this.baseUrl}/users/${encodeURIComponent(keycloakId)}/roles`;
  }
}
