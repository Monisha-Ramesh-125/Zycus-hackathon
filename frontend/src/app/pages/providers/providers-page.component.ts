import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, timer } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { isFuture } from '../../core/local-datetime';
import { NotificationService } from '../../core/notification.service';
import { QueueService } from '../../core/queue.service';
import { ACTIVE_APPOINTMENT_STATUSES, ProviderStatus } from '../../models/enums';
import { AppointmentView, ProviderView, SlotView } from '../../models/views';
import { ProviderCardComponent, ProviderStats } from './provider-card.component';

/** How long to wait for the background loop before reloading the counts. */
const LOOP_SETTLE_MS = 2500;

@Component({
  selector: 'app-providers-page',
  standalone: true,
  imports: [ProviderCardComponent],
  templateUrl: './providers-page.component.html'
})
export class ProvidersPageComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);
  private readonly queue = inject(QueueService);
  private readonly destroyRef = inject(DestroyRef);

  readonly providers = signal<ProviderView[]>([]);
  readonly appointments = signal<AppointmentView[]>([]);
  readonly slots = signal<SlotView[]>([]);
  readonly loading = signal(true);

  readonly stats = computed(() => {
    const result = new Map<string, ProviderStats>();
    const active = this.appointments().filter(a => ACTIVE_APPOINTMENT_STATUSES.includes(a.status));
    this.providers().forEach(provider =>
      result.set(provider.id, {
        upcoming: active.filter(a => a.provider.id === provider.id).length,
        waiting: active.filter(a => a.provider.id === provider.id && a.status === 'RESCHEDULE_PENDING').length,
        openSlots: this.slots().filter(s => s.providerId === provider.id && s.status === 'OPEN' && isFuture(s.startTime)).length
      })
    );
    return result;
  });
  readonly maxUpcoming = computed(() => Math.max(1, ...[...this.stats().values()].map(s => s.upcoming)));

  private readonly emptyStats: ProviderStats = { upcoming: 0, waiting: 0, openSlots: 0 };

  ngOnInit(): void {
    this.load();
  }

  statsFor(provider: ProviderView): ProviderStats {
    return this.stats().get(provider.id) ?? this.emptyStats;
  }

  load(): void {
    forkJoin({ providers: this.api.providers(), appointments: this.api.appointments(), slots: this.api.slots() }).subscribe({
      next: data => {
        this.providers.set(data.providers);
        this.appointments.set(data.appointments);
        this.slots.set(data.slots);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.notifications.error(error);
      }
    });
  }

  onChanged(status: ProviderStatus): void {
    this.load();
    if (status === 'OFFLINE') {
      this.queue.refreshBurst();
      timer(LOOP_SETTLE_MS)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.load());
    }
  }
}
