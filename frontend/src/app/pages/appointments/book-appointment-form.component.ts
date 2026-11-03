import { TitleCasePipe } from '@angular/common';
import { Component, EventEmitter, OnInit, Output, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, of, switchMap, tap } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { toApiError } from '../../core/api-error';
import { APPOINTMENT_TYPE_DISPLAY } from '../../core/display';
import { formatDateTime, isFuture } from '../../core/local-datetime';
import { NotificationService } from '../../core/notification.service';
import { APPOINTMENT_TYPES, AppointmentType } from '../../models/enums';
import { ProviderView, SlotView } from '../../models/views';

@Component({
  selector: 'app-book-appointment-form',
  standalone: true,
  imports: [ReactiveFormsModule, TitleCasePipe],
  templateUrl: './book-appointment-form.component.html'
})
export class BookAppointmentFormComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  @Output() booked = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  readonly types = APPOINTMENT_TYPES;
  readonly typeLabel = (type: AppointmentType): string => APPOINTMENT_TYPE_DISPLAY[type].label;
  readonly providers = signal<ProviderView[]>([]);
  readonly openSlots = signal<SlotView[]>([]);
  readonly loadingSlots = signal(false);
  readonly saving = signal(false);

  readonly form = this.fb.group({
    patientName: ['', [Validators.required, Validators.maxLength(120)]],
    type: ['ROUTINE' as AppointmentType, Validators.required],
    providerId: ['', Validators.required],
    slotId: ['', Validators.required],
    patientPhone: ['', Validators.maxLength(40)],
    notes: ['', Validators.maxLength(500)]
  });

  constructor() {
    this.form.controls.providerId.valueChanges
      .pipe(
        tap(() => {
          this.form.controls.slotId.reset('');
          this.openSlots.set([]);
          this.loadingSlots.set(true);
        }),
        switchMap(providerId =>
          providerId
            ? this.api.slots({ status: 'OPEN', providerId }).pipe(catchError(() => of([] as SlotView[])))
            : of([] as SlotView[])
        ),
        takeUntilDestroyed()
      )
      .subscribe(slots => {
        this.openSlots.set(slots.filter(slot => isFuture(slot.startTime)));
        this.loadingSlots.set(false);
      });
  }

  ngOnInit(): void {
    this.api.providers().subscribe({
      next: providers => this.providers.set(providers),
      error: error => this.notifications.error(error)
    });
  }

  slotLabel(slot: SlotView): string {
    return `${formatDateTime(slot.startTime)} · ${slot.durationMinutes} min`;
  }

  submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .bookAppointment({
        patientName: value.patientName.trim(),
        type: value.type,
        providerId: value.providerId,
        slotId: value.slotId,
        patientPhone: value.patientPhone.trim(),
        notes: value.notes.trim()
      })
      .subscribe({
        next: appointment => {
          this.saving.set(false);
          this.notifications.success(
            `Booked ${appointment.patientName} with ${appointment.provider.name} (${formatDateTime(appointment.slot.startTime)}).`
          );
          this.form.reset({ type: 'ROUTINE' });
          this.booked.emit();
        },
        error: (raw: unknown) => {
          this.saving.set(false);
          const error = toApiError(raw);
          this.notifications.error(error);
          if (error.errorCode === 'SLOT_UNAVAILABLE') {
            this.form.controls.providerId.setValue(value.providerId);
          }
        }
      });
  }
}
