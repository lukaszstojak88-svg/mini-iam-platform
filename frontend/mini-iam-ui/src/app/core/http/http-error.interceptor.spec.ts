import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { httpErrorInterceptor } from './http-error.interceptor';

describe('httpErrorInterceptor', () => {
  const keycloak = { login: vi.fn().mockResolvedValue(undefined) };
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    keycloak.login.mockClear();
    router.navigateByUrl.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: Keycloak, useValue: keycloak },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  function failRequest(url: string, status: number): unknown {
    let received: unknown;
    http.get(url).subscribe({ error: (error: unknown) => (received = error) });
    httpMock.expectOne(url).flush({ status }, { status, statusText: 'error' });
    return received;
  }

  it('re-authenticates on 401 from our API and still propagates the error', () => {
    const error = failRequest('http://localhost:8082/api/auth/me', 401);

    expect(keycloak.login).toHaveBeenCalledOnce();
    expect(error).toBeDefined();
  });

  it('navigates to access denied on 403 from our API', () => {
    failRequest('http://localhost:8081/api/users', 403);

    expect(router.navigateByUrl).toHaveBeenCalledWith('/access-denied');
    expect(keycloak.login).not.toHaveBeenCalled();
  });

  it('ignores errors from other origins', () => {
    failRequest('https://example.com/api/x', 401);

    expect(keycloak.login).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
