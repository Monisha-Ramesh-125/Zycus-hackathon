import { Component, Input, computed, signal } from '@angular/core';

import { dateKeyOf, formatTime, minutesOfDay } from '../../core/local-datetime';
import { SLOT_STATUS_DISPLAY } from '../../core/display';
import { AppointmentView, ProviderView, SlotView } from '../../models/views';

interface BlockModel {
  id: string;
  left: number;
  width: number;
  status: string;
  label: string;
  detail: string;
}

interface RowModel {
  provider: ProviderView;
  blocks: BlockModel[];
}

const MIN_SPAN_MINUTES = 240;

/** One row per provider, slots placed on a shared time axis for a single day. */
@Component({
  selector: 'app-slot-timeline',
  standalone: true,
  templateUrl: './slot-timeline.component.html'
})
export class SlotTimelineComponent {
  private readonly _providers = signal<ProviderView[]>([]);
  private readonly _slots = signal<SlotView[]>([]);
  private readonly _appointments = signal<AppointmentView[]>([]);
  private readonly _date = signal('');

  @Input({ required: true }) set providers(value: ProviderView[]) { this._providers.set(value); }
  @Input({ required: true }) set slots(value: SlotView[]) { this._slots.set(value); }
  @Input({ required: true }) set appointments(value: AppointmentView[]) { this._appointments.set(value); }
  @Input({ required: true }) set date(value: string) { this._date.set(value); }

  private readonly daySlots = computed(() => this._slots().filter(slot => dateKeyOf(slot.startTime) === this._date()));

  readonly axis = computed(() => {
    const slots = this.daySlots();
    if (slots.length === 0) {
      return null;
    }
    const starts = slots.map(slot => minutesOfDay(slot.startTime));
    const ends = slots.map(slot => minutesOfDay(slot.startTime) + slot.durationMinutes);
    let start = Math.floor(Math.min(...starts) / 60) * 60;
    let end = Math.ceil(Math.max(...ends) / 60) * 60;
    if (end - start < MIN_SPAN_MINUTES) {
      end = Math.min(24 * 60, start + MIN_SPAN_MINUTES);
      start = Math.max(0, end - MIN_SPAN_MINUTES);
    }
    const ticks: { label: string; left: number }[] = [];
    for (let minute = start; minute <= end; minute += 60) {
      ticks.push({ label: `${String(Math.floor(minute / 60) % 24).padStart(2, '0')}:00`, left: ((minute - start) / (end - start)) * 100 });
    }
    return { start, span: end - start, ticks };
  });

  readonly rows = computed<RowModel[]>(() => {
    const axis = this.axis();
    if (!axis) {
      return [];
    }
    const patientBySlot = new Map(this._appointments().map(a => [a.slot.id, a.patientName]));
    return this._providers().map(provider => ({
      provider,
      blocks: this.daySlots()
        .filter(slot => slot.providerId === provider.id)
        .map(slot => {
          const start = minutesOfDay(slot.startTime);
          const patient = patientBySlot.get(slot.id);
          const when = `${formatTime(slot.startTime)}, ${slot.durationMinutes} min`;
          const status = SLOT_STATUS_DISPLAY[slot.status].label;
          return {
            id: slot.id,
            left: ((start - axis.start) / axis.span) * 100,
            width: Math.max(3, (slot.durationMinutes / axis.span) * 100),
            status: slot.status,
            label: patient ?? status,
            detail: `${provider.name}: ${when}, ${status}${patient ? `, ${patient}` : ''}`
          };
        })
    }));
  });

  readonly hasSlots = computed(() => this.daySlots().length > 0);
}
