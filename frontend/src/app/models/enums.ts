export type Specialty = 'GENERAL' | 'SPECIALIST' | 'PEDIATRIC';
export type ProviderStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';
export type SlotStatus = 'OPEN' | 'BOOKED' | 'CANCELLED';
export type AppointmentType = 'ROUTINE' | 'URGENT' | 'FOLLOW_UP';
export type AppointmentStatus = 'CONFIRMED' | 'RESCHEDULE_PENDING' | 'RESCHEDULED' | 'COMPLETED';
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';
export type TriggerReason = 'INITIAL' | 'PROVIDER_OFFLINE' | 'SLOT_CANCELLED';

export const APPOINTMENT_TYPES: AppointmentType[] = ['ROUTINE', 'URGENT', 'FOLLOW_UP'];
export const APPOINTMENT_STATUSES: AppointmentStatus[] = ['CONFIRMED', 'RESCHEDULE_PENDING', 'RESCHEDULED', 'COMPLETED'];
export const PROVIDER_STATUSES: ProviderStatus[] = ['AVAILABLE', 'BUSY', 'OFFLINE'];
export const SLOT_STATUSES: SlotStatus[] = ['OPEN', 'BOOKED', 'CANCELLED'];

/** Statuses of appointments that still occupy a provider's calendar. */
export const ACTIVE_APPOINTMENT_STATUSES: AppointmentStatus[] = ['CONFIRMED', 'RESCHEDULE_PENDING', 'RESCHEDULED'];
