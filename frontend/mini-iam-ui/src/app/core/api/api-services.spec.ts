import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { AuditApiService } from './audit-api.service';
import { RoleApiService } from './role-api.service';
import { UserApiService } from './user-api.service';

describe('API services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  describe('UserApiService', () => {
    it('lists users with paging, sort and trimmed search, skipping empty params', () => {
      TestBed.inject(UserApiService).list({ page: 2, size: 50, sort: 'email,asc', search: '  jo ' }).subscribe();

      const req = http.expectOne((r) => r.url === 'http://localhost:8081/api/users');
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      expect(req.request.params.get('sort')).toBe('email,asc');
      expect(req.request.params.get('search')).toBe('jo');
      req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });

      TestBed.inject(UserApiService).list({ page: 0, size: 20, search: '' }).subscribe();
      expect(http.expectOne((r) => r.url.endsWith('/api/users')).request.params.has('search')).toBe(false);
    });

    it('uses the right methods and URLs for commands', () => {
      const api = TestBed.inject(UserApiService);
      api.create({ username: 'jan', email: 'j@x.pl', firstName: null, lastName: null, temporaryPassword: 'Secret-Pass-123' }).subscribe();
      api.update('id-1', { email: 'j@x.pl', firstName: 'Jan', lastName: null }).subscribe();
      api.setEnabled('id-1', false).subscribe();
      api.delete('id-1').subscribe();
      api.updateMe({ email: 'me@x.pl', firstName: null, lastName: null }).subscribe();

      expect(http.expectOne({ method: 'POST', url: 'http://localhost:8081/api/users' }).request.body.temporaryPassword).toBe('Secret-Pass-123');
      expect(http.expectOne({ method: 'PUT', url: 'http://localhost:8081/api/users/id-1' }).request.body.firstName).toBe('Jan');
      expect(http.expectOne({ method: 'PATCH', url: 'http://localhost:8081/api/users/id-1/status' }).request.body).toEqual({ enabled: false });
      http.expectOne({ method: 'DELETE', url: 'http://localhost:8081/api/users/id-1' });
      http.expectOne({ method: 'PUT', url: 'http://localhost:8081/api/users/me' });
    });
  });

  describe('RoleApiService', () => {
    it('reads, assigns and removes roles by keycloakId on auth-service', () => {
      const api = TestBed.inject(RoleApiService);
      api.listRoles().subscribe();
      api.getUserRoles('kc-1').subscribe();
      api.assign('kc-1', 'MANAGER').subscribe();
      api.remove('kc-1', 'AUDITOR').subscribe();

      http.expectOne({ method: 'GET', url: 'http://localhost:8082/api/roles' });
      http.expectOne({ method: 'GET', url: 'http://localhost:8082/api/users/kc-1/roles' });
      expect(http.expectOne({ method: 'POST', url: 'http://localhost:8082/api/users/kc-1/roles' }).request.body).toEqual({
        roleName: 'MANAGER',
      });
      http.expectOne({ method: 'DELETE', url: 'http://localhost:8082/api/users/kc-1/roles/AUDITOR' });
    });
  });

  describe('AuditApiService', () => {
    it('sends only the filters that are set', () => {
      TestBed.inject(AuditApiService)
        .list({ page: 0, size: 20, sort: 'occurredAt,desc', eventType: 'ROLE_ASSIGNED', actorUserId: '', from: '2026-11-01T00:00:00.000Z' })
        .subscribe();

      const params = http.expectOne((r) => r.url === 'http://localhost:8083/api/audit-events').request.params;
      expect(params.get('eventType')).toBe('ROLE_ASSIGNED');
      expect(params.get('from')).toBe('2026-11-01T00:00:00.000Z');
      expect(params.has('actorUserId')).toBe(false);
      expect(params.has('to')).toBe(false);
    });
  });
});
