import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AppRole } from '../auth/app-role';

/** Response of auth-service `GET /api/auth/me`. `userId` is the Keycloak user id. */
export interface CurrentUser {
  userId: string;
  username: string | null;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  roles: AppRole[];
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);

  getCurrentUser(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${environment.api.authService}/api/auth/me`);
  }
}
