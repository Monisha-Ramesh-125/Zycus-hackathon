import { AppointmentStatus, AppointmentType, ProviderStatus, SlotStatus, TriggerReason } from '../models/enums';

export type Tone = 'green' | 'amber' | 'red' | 'blue' | 'purple' | 'grey' | 'teal';

export interface DisplayModel {
  label: string;
  tone: Tone;
}

export const APPOINTMENT_TYPE_DISPLAY: Record<AppointmentType, DisplayModel> = {
  ROUTINE: { label: 'Routine', tone: 'blue' },
  URGENT: { label: 'Urgent', tone: 'red' },
  FOLLOW_UP: { label: 'Follow-up', tone: 'purple' }
};

export const APPOINTMENT_STATUS_DISPLAY: Record<AppointmentStatus, DisplayModel> = {
  CONFIRMED: { label: 'Confirmed', tone: 'green' },
  RESCHEDULE_PENDING: { label: 'Needs reschedule', tone: 'amber' },
  RESCHEDULED: { label: 'Rescheduled', tone: 'blue' },
  COMPLETED: { label: 'Completed', tone: 'grey' }
};

export const PROVIDER_STATUS_DISPLAY: Record<ProviderStatus, DisplayModel> = {
  AVAILABLE: { label: 'Available', tone: 'green' },
  BUSY: { label: 'Busy', tone: 'amber' },
  OFFLINE: { label: 'Offline', tone: 'red' }
};

export const SLOT_STATUS_DISPLAY: Record<SlotStatus, DisplayModel> = {
  OPEN: { label: 'Open', tone: 'green' },
  BOOKED: { label: 'Booked', tone: 'blue' },
  CANCELLED: { label: 'Cancelled', tone: 'red' }
};

export const TRIGGER_DISPLAY: Record<TriggerReason, DisplayModel> = {
  PROVIDER_OFFLINE: { label: 'Provider offline', tone: 'red' },
  SLOT_CANCELLED: { label: 'Slot cancelled', tone: 'amber' },
  INITIAL: { label: 'Requested by staff', tone: 'grey' }
};

/** How a suggestion's strategy label is shown; "->rule-fallback" means the AI failed and rules answered. */
export function strategyDisplay(strategy: string): DisplayModel & { hint: string } {
  if (strategy.includes('fallback')) {
    return { label: 'Rules (AI unavailable)', tone: 'amber', hint: 'The AI could not answer, so the rule-based strategy was used.' };
  }
  if (strategy === 'ai') {
    return { label: 'AI suggestion', tone: 'teal', hint: 'Chosen by the AI model from the open slots.' };
  }
  if (strategy === 'rule') {
    return { label: 'Rule-based', tone: 'grey', hint: 'Chosen by the built-in scheduling rules.' };
  }
  return { label: strategy, tone: 'grey', hint: '' };
}
