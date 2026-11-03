import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { toApiError } from '../../core/api-error';
import { SLOT_STATUS_DISPLAY } from '../../core/display';
import { toDateKey, toLocalDateTimeString } from '../../core/local-datetime';
import { NotificationService } from '../../core/notification.service';
import { QueueService } from '../../core/queue.service';
import { SLOT_STATUSES, SlotStatus } from '../../models/enums';
import { AppointmentView, ProviderView, SlotView } from '../../models/views';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { LocalDatePipe } from '../../shared/local-date.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { SlotTimelineComponent } from './slot-timeline.component';

const DAY_MS = 24 * 60 * 60 * 1000;

/** The next whole hour, formatted for a datetime-local input. */
function nextHourInputValue(): string {
  const date = new Date();
  date.setMinutes(0, 0, 0);
  date.setHours(date.getHours() + 1);
  return toLocalDateTimeString(date).slice(0, 16);
}

@Component({
  selector: 'app-slots-page',
  standalone: true,
  imports: [ReactiveFormsModule, StatusBadgeComponent, ConfirmDialogComponent, LocalDatePipe, SlotTimelineComponent],
  templateUrl: './slots-page.component.html'
})
export class SlotsPageComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);
  private readonly queue = inject(QueueService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly statuses = SLOT_STATUSES;
  readonly statusLabel = (status: SlotStatus): string => SLOT_STATUS_DISPLAY[status].label;

  readonly providers = signal<ProviderView[]>([]);
  readonly slots = signal<SlotView[]>([]);
  readonly appointments = signal<AppointmentView[]>([]);
  readonly loading = signal(true);

  readonly statusFilter = signal<SlotStatus | ''>('');
  readonly providerFilter = signal('');
  readonly day = signal(toDateKey(new Date()));

  readonly visible = computed(() =>
    this.slots().filter(
      slot =>
        (!this.statusFilter() || slot.status === this.statusFilter()) &&
        (!this.providerFilter() || slot.providerId === this.providerFilter())
    )
  );

  readonly toCancel = signal<SlotView | null>(null);
  readonly cancelReason = signal('');
  readonly cancelling = signal(false);

  readonly showCreate = signal(false);
  readonly creating = signal(false);
  readonly createForm = this.fb.group({
    providerId: ['', Validators.required],
    startTime: [nextHourInputValue(), Validators.required],
    durationMinutes: [30, [Validators.required, Validators.min(5), Validators.max(480)]]
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    forkJoin({ providers: this.api.providers(), slots: this.api.slots(), appointments: this.api.appointments() }).subscribe({
      next: data => {
        this.providers.set(data.providers);
        this.slots.set(data.slots);
        this.appointments.set(data.appointments);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.notifications.error(error);
      }
    });
  }

  shiftDay(days: number): void {
    const [year, month, date] = this.day().split('-').map(Number);
    this.day.set(toDateKey(new Date(new Date(year, month - 1, date).getTime() + days * DAY_MS + DAY_MS / 2)));
  }

  setDay(value: string): void {
    if (value) {
      this.day.set(value);
    }
  }

  askCancel(slot: SlotView): void {
    this.toCancel.set(slot);
    this.cancelReason.set('');
  }

  confirmCancel(): void {
    const slot = this.toCancel();
    if (!slot) {
      return;
    }
    this.cancelling.set(true);
    this.api.cancelSlot(slot.id, this.cancelReason().trim()).subscribe({
      next: () => {
        this.cancelling.set(false);
        this.toCancel.set(null);
        this.notifications.success(
          slot.status === 'BOOKED'
            ? `Slot cancelled. The booked appointment is being rescheduled; check the queue shortly.`
            : `Slot cancelled.`,
          slot.status === 'BOOKED' ? { label: 'Open queue', route: '/queue' } : undefined
        );
        this.load();
        if (slot.status === 'BOOKED') {
          this.queue.refreshBurst();
        }
      },
      error: (raw: unknown) => {
        this.cancelling.set(false);
        this.toCancel.set(null);
        this.notifications.error(toApiError(raw));
        this.load();
      }
    });
  }

  create(): void {
    if (this.createForm.invalid || this.creating()) {
      this.createForm.markAllAsTouched();
      return;
    }
    const value = this.createForm.getRawValue();
    this.creating.set(true);
    this.api
      .createSlot({
        providerId: value.providerId,
        startTime: `${value.startTime.slice(0, 16)}:00`,
        durationMinutes: value.durationMinutes
      })
      .subscribe({
        next: slot => {
          this.creating.set(false);
          this.notifications.success(`Open slot created for ${slot.providerName}.`);
          this.day.set(slot.startTime.slice(0, 10));
          this.createForm.patchValue({ startTime: nextHourInputValue() });
          this.load();
        },
        error: (raw: unknown) => {
          this.creating.set(false);
          this.notifications.error(toApiError(raw));
        }
      });
  }
}
