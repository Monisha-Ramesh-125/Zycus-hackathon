import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, distinctUntilChanged, fromEvent, map, merge, startWith, switchMap, timer } from 'rxjs';

import { QUEUE_POLL_INTERVAL_MS } from './api.config';
import { ApiService } from './api.service';
import { AppointmentView } from '../models/views';

/** Delays after a provider goes offline, to catch the suggestions the background loop creates. */
const BURST_DELAYS_MS = [1000, 3000, 6000];

/** The appointments waiting for a reschedule, polled while the tab is visible and shared by every page. */
@Injectable({ providedIn: 'root' })
export class QueueService {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshRequests = new Subject<void>();
  private started = false;

  readonly appointments = signal<AppointmentView[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly lastUpdated = signal<Date | null>(null);
  readonly waitingCount = computed(() => this.appointments().length);

  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;

    const visible$ = fromEvent(document, 'visibilitychange').pipe(
      map(() => !document.hidden),
      startWith(!document.hidden),
      distinctUntilChanged()
    );
    const polls$ = visible$.pipe(switchMap(visible => (visible ? timer(0, QUEUE_POLL_INTERVAL_MS) : EMPTY)));

    merge(polls$, this.refreshRequests)
      .pipe(
        switchMap(() =>
          this.api.appointments('RESCHEDULE_PENDING').pipe(
            catchError(() => {
              this.failed.set(true);
              this.loading.set(false);
              return EMPTY;
            })
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(list => {
        this.appointments.set(list);
        this.failed.set(false);
        this.loading.set(false);
        this.lastUpdated.set(new Date());
      });
  }

  refresh(): void {
    this.refreshRequests.next();
  }

  refreshBurst(): void {
    this.refresh();
    BURST_DELAYS_MS.forEach(delay =>
      timer(delay)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.refresh())
    );
  }
}
