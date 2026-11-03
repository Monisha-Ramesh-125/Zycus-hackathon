import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { ApiService } from '../../core/api.service';
import { toApiError } from '../../core/api-error';
import { NotificationService } from '../../core/notification.service';
import { QueueService } from '../../core/queue.service';
import { APPOINTMENT_STATUSES, AppointmentStatus } from '../../models/enums';
import { APPOINTMENT_STATUS_DISPLAY } from '../../core/display';
import { AppointmentView } from '../../models/views';
import { LocalDatePipe } from '../../shared/local-date.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { BookAppointmentFormComponent } from './book-appointment-form.component';

type StatusFilter = AppointmentStatus | 'ALL';

@Component({
  selector: 'app-appointments-page',
  standalone: true,
  imports: [StatusBadgeComponent, LocalDatePipe, BookAppointmentFormComponent],
  templateUrl: './appointments-page.component.html'
})
export class AppointmentsPageComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);
  private readonly queue = inject(QueueService);

  readonly statuses = APPOINTMENT_STATUSES;
  readonly statusLabel = (status: AppointmentStatus): string => APPOINTMENT_STATUS_DISPLAY[status].label;

  readonly all = signal<AppointmentView[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly filter = signal<StatusFilter>('ALL');
  readonly showForm = signal(false);
  readonly busyId = signal<string | null>(null);

  readonly visible = computed(() => {
    const filter = this.filter();
    return this.all().filter(appointment => filter === 'ALL' || appointment.status === filter);
  });
  readonly counts = computed(() => {
    const counts = new Map<StatusFilter, number>([['ALL', this.all().length]]);
    this.statuses.forEach(status => counts.set(status, this.all().filter(a => a.status === status).length));
    return counts;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.appointments().subscribe({
      next: list => {
        this.all.set([...list].sort((a, b) => a.slot.startTime.localeCompare(b.slot.startTime)));
        this.failed.set(false);
        this.loading.set(false);
      },
      error: error => {
        this.failed.set(true);
        this.loading.set(false);
        this.notifications.error(error);
      }
    });
  }

  setFilter(filter: StatusFilter): void {
    this.filter.set(filter);
  }

  canSuggest(appointment: AppointmentView): boolean {
    return appointment.status !== 'COMPLETED';
  }

  suggest(appointment: AppointmentView): void {
    this.busyId.set(appointment.id);
    this.api.suggest(appointment.id).subscribe({
      next: suggestion => {
        this.busyId.set(null);
        this.notifications.success(
          `Suggestion ready for ${appointment.patientName}: ${suggestion.recommendedSlot.providerName}. Review it in the queue.`,
          { label: 'Open queue', route: '/queue' }
        );
        this.load();
        this.queue.refresh();
      },
      error: (raw: unknown) => {
        this.busyId.set(null);
        const error = toApiError(raw);
        if (error.errorCode === 'NO_SLOT_AVAILABLE') {
          this.notifications.info(`No open slot fits ${appointment.patientName} right now.`);
        } else {
          this.notifications.error(error);
        }
        this.load();
        this.queue.refresh();
      }
    });
  }

  onBooked(): void {
    this.showForm.set(false);
    this.load();
  }
}
