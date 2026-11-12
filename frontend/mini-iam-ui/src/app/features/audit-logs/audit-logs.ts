import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, EMPTY, map, switchMap, tap } from 'rxjs';

import { AUDIT_EVENT_TYPES, AuditEvent, AuditEventType, PAGE_SIZES } from '../../core/api/api-models';
import { AuditApiService } from '../../core/api/audit-api.service';
import { toApiError } from '../../core/http/api-error';
import { readPage, readSize, readSort, sortParam, SortState } from '../../shared/list-query';
import { LoadState, LoadStatus } from '../../shared/load-state';
import { PageHeader } from '../../shared/page-header';
import { StatusPill } from '../../shared/status-pill';
import { AuditEventDialog } from './audit-event-dialog';
import {
  actorName,
  AuditFilters,
  fromLocalDateTime,
  readFilters,
  shortId,
  targetName,
  toApiRange,
  toLocalDateTime,
} from './audit-filters';
import { DATE_TIME_PIPE_FORMAT } from '../../shared/date-time-adapter';
import { DateTimeField } from '../../shared/date-time-field';

/** `from` must not be later than `to` (both inclusive, to the second). */
function rangeOrder(group: AbstractControl): ValidationErrors | null {
  const from = group.get('from')?.value as Date | null;
  const to = group.get('to')?.value as Date | null;
  const valid = (d: Date | null): d is Date => !!d && !isNaN(d.getTime());
  return valid(from) && valid(to) && from.getTime() > to.getTime() ? { rangeOrder: true } : null;
}

const SORT_FIELDS = ['occurredAt', 'eventType'] as const;
const DEFAULT_SORT: SortState = { active: 'occurredAt', direction: 'desc' };

/**
 * Read-only audit log (ADMIN, AUDITOR; spec §6.3): server-side filters, paging and sorting kept in the URL.
 * Actor and target are shown by the usernames stored in the event by the publishing service (full Keycloak id in
 * a tooltip); older events without them fall back to `details.username` or the shortened id. Names are not
 * resolved from user-service, which AUDITOR cannot read.
 */
@Component({
  selector: 'app-audit-logs',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    DateTimeField,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    MatTooltipModule,
    LoadState,
    PageHeader,
    StatusPill,
  ],
  templateUrl: './audit-logs.html',
  styleUrl: './audit-logs.scss',
})
export class AuditLogs {
  private readonly auditApi = inject(AuditApiService);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly eventTypes = AUDIT_EVENT_TYPES;
  protected readonly pageSizes = PAGE_SIZES;
  protected readonly columns = ['occurredAt', 'eventType', 'actor', 'target', 'actions'];
  protected readonly shortId = shortId;
  protected readonly actorName = actorName;
  protected readonly targetName = targetName;

  protected readonly status = signal<LoadStatus>('loading');
  protected readonly errorMessage = signal('');
  protected readonly events = signal<AuditEvent[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly sort = signal<SortState>(DEFAULT_SORT);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly dateTimeFormat = DATE_TIME_PIPE_FORMAT;

  protected readonly filters = new FormGroup(
    {
      eventType: new FormControl<AuditEventType | null>(null),
      actorUserId: new FormControl('', { nonNullable: true }),
      targetUserId: new FormControl('', { nonNullable: true }),
      from: new FormControl<Date | null>(null),
      to: new FormControl<Date | null>(null),
    },
    { validators: rangeOrder },
  );


  constructor() {
    combineLatest([this.route.queryParamMap, this.reload$])
      .pipe(
        map(([params]) => ({
          page: readPage(params),
          size: readSize(params),
          sort: readSort(params, SORT_FIELDS, DEFAULT_SORT),
          filters: readFilters(params),
        })),
        tap((query) => {
          this.page.set(query.page);
          this.size.set(query.size);
          this.sort.set(query.sort);
          this.showFilters(query.filters);
          this.status.set('loading');
        }),
        switchMap((query) =>
          this.auditApi
            .list({
              page: query.page,
              size: query.size,
              sort: sortParam(query.sort),
              eventType: query.filters.eventType ?? undefined,
              actorUserId: query.filters.actorUserId,
              targetUserId: query.filters.targetUserId,
              ...toApiRange(query.filters),
            })
            .pipe(
              catchError((error: unknown) => {
                this.errorMessage.set(toApiError(error).message);
                this.status.set('error');
                return EMPTY;
              }),
            ),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((page) => {
        if (page.content.length === 0 && page.page > 0 && page.totalElements > 0) {
          // Page beyond the end (old link): go to the last existing page.
          this.navigate({ page: page.totalPages - 1 || null });
          return;
        }
        this.events.set(page.content);
        this.total.set(page.totalElements);
        this.status.set('loaded');
      });
  }

  protected applyFilters(): void {
    if (this.filters.invalid) {
      // e.g. a date typed by hand that could not be parsed - do not silently search without it.
      this.filters.markAllAsTouched();
      return;
    }
    const value = this.filters.getRawValue();
    this.navigate({
      eventType: value.eventType,
      actor: value.actorUserId.trim() || null,
      target: value.targetUserId.trim() || null,
      from: toLocalDateTime(value.from),
      to: toLocalDateTime(value.to),
      page: null,
    });
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.navigate({ eventType: null, actor: null, target: null, from: null, to: null, page: null });
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

  protected openDetails(event: AuditEvent): void {
    this.dialog.open(AuditEventDialog, { data: event, width: '640px', maxWidth: '95vw' });
  }

  protected roleName(event: AuditEvent): string | null {
    const value = event.details?.['roleName'];
    return typeof value === 'string' ? value : null;
  }

  private showFilters(filters: AuditFilters): void {
    this.filters.setValue(
      {
        eventType: filters.eventType,
        actorUserId: filters.actorUserId,
        targetUserId: filters.targetUserId,
        from: fromLocalDateTime(filters.from),
        to: fromLocalDateTime(filters.to),
      },
      { emitEvent: false },
    );
  }

  private navigate(queryParams: Params): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
