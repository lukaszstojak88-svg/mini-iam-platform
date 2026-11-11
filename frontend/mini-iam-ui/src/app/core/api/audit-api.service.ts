import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuditEvent, AuditQuery, PageResponse } from './api-models';
import { toHttpParams } from './http-params';

/** audit-service read API (spec §6.3). Audit entries are read-only. */
@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.api.auditService}/api/audit-events`;

  list(query: AuditQuery): Observable<PageResponse<AuditEvent>> {
    return this.http.get<PageResponse<AuditEvent>>(this.baseUrl, { params: toHttpParams(query) });
  }
}
