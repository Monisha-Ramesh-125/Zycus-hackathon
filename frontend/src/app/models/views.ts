import {
  AppointmentStatus,
  AppointmentType,
  ProviderStatus,
  SlotStatus,
  Specialty,
  SuggestionStatus,
  TriggerReason
} from './enums';

export interface ProviderView {
  id: string;
  name: string;
  specialty: Specialty;
  status: ProviderStatus;
  statusReason: string | null;
  statusChangedAt: string | null;
}

export interface SlotView {
  id: string;
  providerId: string;
  providerName: string;
  startTime: string;
  durationMinutes: number;
  status: SlotStatus;
  statusReason: string | null;
}

export interface SuggestionView {
  id: string;
  appointmentId: string;
  patientName: string;
  recommendedSlot: SlotView;
  alternateProvider: ProviderView | null;
  confidence: number;
  reasoning: string;
  strategy: string;
  status: SuggestionStatus;
  triggerReason: TriggerReason;
  createdAt: string;
  decidedAt: string | null;
  decisionNote: string | null;
}

export interface AppointmentView {
  id: string;
  patientName: string;
  type: AppointmentType;
  status: AppointmentStatus;
  patientPhone: string | null;
  notes: string | null;
  rescheduleCount: number;
  createdAt: string;
  modifiedAt: string;
  provider: ProviderView;
  slot: SlotView;
  pendingSuggestion: SuggestionView | null;
}

export interface StrategyView {
  active: string;
  available: string[];
}
