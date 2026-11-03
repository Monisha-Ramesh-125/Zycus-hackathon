import { Component, Input } from '@angular/core';

import {
  APPOINTMENT_STATUS_DISPLAY,
  APPOINTMENT_TYPE_DISPLAY,
  DisplayModel,
  PROVIDER_STATUS_DISPLAY,
  SLOT_STATUS_DISPLAY,
  TRIGGER_DISPLAY
} from '../core/display';

export type BadgeKind = 'appointmentType' | 'appointmentStatus' | 'providerStatus' | 'slotStatus' | 'trigger';

const TABLES: Record<BadgeKind, Record<string, DisplayModel>> = {
  appointmentType: APPOINTMENT_TYPE_DISPLAY,
  appointmentStatus: APPOINTMENT_STATUS_DISPLAY,
  providerStatus: PROVIDER_STATUS_DISPLAY,
  slotStatus: SLOT_STATUS_DISPLAY,
  trigger: TRIGGER_DISPLAY
};

/** Text always carries the meaning; colour only reinforces it. */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span class="badge" [attr.data-tone]="display.tone">{{ display.label }}</span>`
})
export class StatusBadgeComponent {
  @Input({ required: true }) kind!: BadgeKind;
  @Input({ required: true }) value!: string;

  get display(): DisplayModel {
    return TABLES[this.kind][this.value] ?? { label: this.value, tone: 'grey' };
  }
}
