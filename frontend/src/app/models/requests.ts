import { AppointmentType, ProviderStatus, SlotStatus } from './enums';

export interface BookAppointmentModel {
  patientName: string;
  type: AppointmentType;
  providerId: string;
  slotId: string;
  patientPhone?: string;
  notes?: string;
}

export interface UpdateProviderStatusModel {
  status: ProviderStatus;
  reason?: string;
}

export interface UpdateSlotStatusModel {
  status: SlotStatus;
  reason?: string;
}

export interface CreateSlotModel {
  providerId: string;
  startTime: string;
  durationMinutes: number;
}

export interface SuggestionDecisionModel {
  decision: 'ACCEPT' | 'REJECT';
  note?: string;
}

export interface SwitchStrategyModel {
  strategy: string;
}
