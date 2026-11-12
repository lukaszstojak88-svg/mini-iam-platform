import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreateUserRequest, PageResponse, UpdateProfileRequest, User, UserDetails, UserQuery } from './api-models';
import { toHttpParams } from './http-params';

/** user-service API (spec §6.1). */
@Injectable({ providedIn: 'root' })
export class UserApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.api.userService}/api/users`;

  list(query: UserQuery): Observable<PageResponse<User>> {
    return this.http.get<PageResponse<User>>(this.baseUrl, { params: toHttpParams(query) });
  }

  get(id: string): Observable<UserDetails> {
    return this.http.get<UserDetails>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  getMe(): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/me`);
  }

  updateMe(request: UpdateProfileRequest): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/me`, request);
  }

  create(request: CreateUserRequest): Observable<User> {
    return this.http.post<User>(this.baseUrl, request);
  }

  update(id: string, request: UpdateProfileRequest): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/${encodeURIComponent(id)}`, request);
  }

  setEnabled(id: string, enabled: boolean): Observable<User> {
    return this.http.patch<User>(`${this.baseUrl}/${encodeURIComponent(id)}/status`, { enabled });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }
}
