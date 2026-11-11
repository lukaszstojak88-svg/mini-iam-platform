import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { authAs } from '../../../testing/test-helpers';
import { AppRole } from '../../core/auth/app-role';
import { AuthService } from '../../core/auth/auth.service';
import { Shell } from './shell';

function menuFor(roles: AppRole[]): string[] {
  TestBed.configureTestingModule({
    imports: [Shell],
    providers: [provideRouter([]), { provide: AuthService, useValue: authAs(roles) }],
  });
  const fixture = TestBed.createComponent(Shell);
  fixture.detectChanges();
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('[matlistitemtitle]')).map(
    (item) => item.textContent?.trim() ?? '',
  );
}

describe('Shell menu (spec §2.1)', () => {
  it('shows every section to ADMIN', () => {
    expect(menuFor(['ADMIN', 'USER'])).toEqual(['Dashboard', 'Users', 'Roles', 'Audit log']);
  });

  it('shows users but not roles or audit to MANAGER', () => {
    expect(menuFor(['MANAGER', 'USER'])).toEqual(['Dashboard', 'Users']);
  });

  it('shows the audit log to AUDITOR', () => {
    expect(menuFor(['AUDITOR', 'USER'])).toEqual(['Dashboard', 'Audit log']);
  });

  it('shows only the dashboard to USER', () => {
    expect(menuFor(['USER'])).toEqual(['Dashboard']);
  });
});
