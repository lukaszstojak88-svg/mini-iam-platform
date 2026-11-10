import { TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { AuthApiService, CurrentUser } from '../../core/api/auth-api.service';
import { Dashboard } from './dashboard';

function render(api: Partial<AuthApiService>) {
  TestBed.configureTestingModule({
    imports: [Dashboard],
    providers: [{ provide: AuthApiService, useValue: api }],
  });
  const fixture = TestBed.createComponent(Dashboard);
  fixture.detectChanges();
  return fixture;
}

const query = (root: HTMLElement, testId: string) => root.querySelector(`[data-testid="${testId}"]`);

describe('Dashboard', () => {
  it('shows a loading indicator, then the user profile with roles', async () => {
    const response = new Subject<CurrentUser>();
    const fixture = render({ getCurrentUser: () => response });
    const root = fixture.nativeElement as HTMLElement;

    expect(query(root, 'loading')).not.toBeNull();

    response.next({
      userId: 'kc-1',
      username: 'admin',
      email: 'admin@example.com',
      firstName: 'Admin',
      lastName: 'Mini IAM',
      roles: ['ADMIN', 'USER'],
    });
    response.complete();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(query(root, 'loading')).toBeNull();
    const profile = query(root, 'profile');
    expect(profile?.textContent).toContain('admin@example.com');
    expect(profile?.textContent).toContain('ADMIN');
    expect(profile?.textContent).toContain('USER');
  });

  it('shows an error state when the API call fails', async () => {
    const fixture = render({ getCurrentUser: () => throwError(() => new Error('down')) });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(query(fixture.nativeElement as HTMLElement, 'error')).not.toBeNull();
  });
});
