import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, debounceTime, EMPTY, map, switchMap, tap } from 'rxjs';

import { PAGE_SIZES, User, USER_SORT_FIELDS } from '../../core/api/api-models';
import { UserApiService } from '../../core/api/user-api.service';
import { toApiError } from '../../core/http/api-error';
import { readPage, readSize, readSort, sortParam, SortState } from '../../shared/list-query';
import { LoadState, LoadStatus } from '../../shared/load-state';
import { PageHeader } from '../../shared/page-header';
import { StatusPill } from '../../shared/status-pill';

const DEFAULT_SORT: SortState = { active: 'createdAt', direction: 'desc' };

/**
 * User list (spec §6.1): server-side search, paging and sorting. The list state lives in the query params,
 * so a page refresh or a link keeps it. Sorting is limited to the fields the API supports.
 */
@Component({
  selector: 'app-users',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatSortModule,
    MatTableModule,
    MatTooltipModule,
    LoadState,
    PageHeader,
    StatusPill,
  ],
  templateUrl: './users.html',
  styleUrl: './users.scss',
})
export class Users {
  private readonly userApi = inject(UserApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly pageSizes = PAGE_SIZES;
  protected readonly columns = ['user', 'email', 'enabled', 'createdAt', 'actions'];

  protected readonly status = signal<LoadStatus>('loading');
  protected readonly errorMessage = signal('');
  protected readonly users = signal<User[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly sort = signal<SortState>(DEFAULT_SORT);
  protected readonly search = new FormControl('', { nonNullable: true });
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  constructor() {
    const destroyRef = inject(DestroyRef);

    combineLatest([this.route.queryParamMap, this.reload$])
      .pipe(
        map(([params]) => ({
          page: readPage(params),
          size: readSize(params),
          sort: readSort(params, USER_SORT_FIELDS, DEFAULT_SORT),
          search: (params.get('search') ?? '').slice(0, 100),
        })),
        tap((query) => {
          this.page.set(query.page);
          this.size.set(query.size);
          this.sort.set(query.sort);
          if (this.search.value !== query.search) {
            this.search.setValue(query.search, { emitEvent: false });
          }
          this.status.set('loading');
        }),
        switchMap((query) =>
          this.userApi.list({ page: query.page, size: query.size, sort: sortParam(query.sort), search: query.search }).pipe(
            catchError((error: unknown) => {
              this.errorMessage.set(toApiError(error).message);
              this.status.set('error');
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((page) => {
        if (page.content.length === 0 && page.page > 0 && page.totalElements > 0) {
          // Page beyond the end (old link, last item deleted): go to the last existing page.
          this.navigate({ page: page.totalPages - 1 || null });
          return;
        }
        this.users.set(page.content);
        this.total.set(page.totalElements);
        this.status.set('loaded');
      });

    // No distinctUntilChanged: the URL may have been reset meanwhile (menu link), so the same text must search again.
    // Navigating to an identical URL is ignored by the router anyway.
    this.search.valueChanges
      .pipe(debounceTime(300), map((value) => value.trim()), takeUntilDestroyed(destroyRef))
      .subscribe((search) => this.navigate({ search: search || null, page: null }));
  }

  protected onSort(sort: Sort): void {
    const next: SortState = sort.direction ? { active: sort.active, direction: sort.direction } : DEFAULT_SORT;
    this.navigate({ sort: sortParam(next), page: null });
  }

  protected onPage(event: PageEvent): void {
    this.navigate({ page: event.pageIndex || null, size: event.pageSize === 20 ? null : event.pageSize });
  }

  protected retry(): void {
    this.reload$.next();
  }

  protected fullName(user: User): string {
    return [user.firstName, user.lastName].filter(Boolean).join(' ') || user.username;
  }

  private navigate(queryParams: Params): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
