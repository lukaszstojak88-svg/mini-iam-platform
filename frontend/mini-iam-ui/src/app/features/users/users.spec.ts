import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { byTestId, page, problem, user } from '../../../testing/test-helpers';
import { UserApiService } from '../../core/api/user-api.service';
import { Users } from './users';

describe('Users', () => {
  const list = vi.fn();

  beforeEach(() => {
    list.mockReset();
    list.mockReturnValue(of(page([user(), user({ id: 'id-2', username: 'ewa', enabled: false })], 42)));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'users', component: Users }]),
        { provide: UserApiService, useValue: { list } },
      ],
    });
  });

  it('loads the list using the state from the query params', async () => {
    const harness = await RouterTestingHarness.create('/users?page=2&size=50&sort=email,asc&search=jan');
    const root = harness.routeNativeElement!;

    expect(list).toHaveBeenLastCalledWith({ page: 2, size: 50, sort: 'email,asc', search: 'jan' });
    expect(root.querySelectorAll('[data-testid="user-row"]').length).toBe(2);
    expect(root.textContent).toContain('Inactive');
  });

  it('ignores unsupported sort fields and page sizes from the URL', async () => {
    await RouterTestingHarness.create('/users?size=500&sort=password,asc');

    expect(list).toHaveBeenLastCalledWith({ page: 0, size: 20, sort: 'createdAt,desc', search: '' });
  });

  it('puts the debounced search into the URL and resets the page', async () => {
    vi.useFakeTimers();
    try {
      const harness = await RouterTestingHarness.create('/users?page=3');
      const input = byTestId(harness.routeNativeElement!, 'search') as HTMLInputElement;
      input.value = 'kowal';
      input.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(300);
      await vi.runAllTimersAsync();

      const url = TestBed.inject(Router).url;
      expect(url).toContain('search=kowal');
      expect(url).not.toContain('page=3');
    } finally {
      vi.useRealTimers();
    }
  });

  it('searches again for the same text after the URL was reset', async () => {
    vi.useFakeTimers();
    try {
      const harness = await RouterTestingHarness.create('/users?search=jo');
      const router = TestBed.inject(Router);
      await harness.navigateByUrl('/users');
      const input = byTestId(harness.routeNativeElement!, 'search') as HTMLInputElement;
      expect(input.value).toBe('');

      input.value = 'jo';
      input.dispatchEvent(new Event('input'));
      await vi.advanceTimersByTimeAsync(300);
      await vi.runAllTimersAsync();

      expect(router.url).toContain('search=jo');
    } finally {
      vi.useRealTimers();
    }
  });

  it('moves to the last page when the requested page is beyond the end', async () => {
    list.mockImplementation((query: { page: number }) =>
      of(query.page === 9 ? { ...page([], 25), page: 9, totalPages: 2 } : { ...page([user()], 25), page: 1, totalPages: 2 }),
    );
    const harness = await RouterTestingHarness.create('/users?page=9');
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toContain('page=1');
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
  });

  it('shows the empty state', async () => {
    list.mockReturnValue(of(page([])));
    const harness = await RouterTestingHarness.create('/users');

    expect(byTestId(harness.routeNativeElement!, 'empty')).not.toBeNull();
  });

  it('shows an error with retry', async () => {
    list.mockReturnValue(throwError(() => problem(503)));
    const harness = await RouterTestingHarness.create('/users');
    const root = harness.routeNativeElement!;

    expect(byTestId(root, 'error')?.textContent).toContain('temporarily unavailable');
    list.mockReturnValue(of(page([user()])));
    root.querySelector<HTMLButtonElement>('[data-testid="error"] button')!.click();
    harness.detectChanges();

    expect(root.querySelectorAll('[data-testid="user-row"]').length).toBe(1);
  });
});
