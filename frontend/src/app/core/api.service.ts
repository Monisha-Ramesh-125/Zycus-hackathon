import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AppointmentStatus, SlotStatus } from '../models/enums';
import {
  BookAppointmentModel,
  CreateSlotModel,
  SuggestionDecisionModel,
  SwitchStrategyModel,
  UpdateProviderStatusModel,
  UpdateSlotStatusModel
} from '../models/requests';
import { AppointmentView, ProviderView, SlotView, StrategyView, SuggestionView } from '../models/views';
import { API_BASE } from './api.config';

/** Blank optional text is left out so the backend sees "not provided" rather than an empty string. */
function withoutBlanks<T extends object>(model: T): T {
  const entries = Object.entries(model).filter(([, value]) => value !== undefined && value !== null && value !== '');
  return Object.fromEntries(entries) as T;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  ping(): Observable<unknown> {
    return this.http.get(`${API_BASE}/ping`);
  }

  strategy(): Observable<StrategyView> {
    return this.http.get<StrategyView>(`${API_BASE}/scheduling/strategy`);
  }

  switchStrategy(strategy: string): Observable<StrategyView> {
    const body: SwitchStrategyModel = { strategy };
    return this.http.put<StrategyView>(`${API_BASE}/scheduling/strategy`, body);
  }

  providers(): Observable<ProviderView[]> {
    return this.http.get<ProviderView[]>(`${API_BASE}/providers`);
  }

  updateProviderStatus(id: string, model: UpdateProviderStatusModel): Observable<ProviderView> {
    return this.http.patch<ProviderView>(`${API_BASE}/providers/${id}/status`, withoutBlanks(model));
  }

  slots(filter: { status?: SlotStatus; providerId?: string } = {}): Observable<SlotView[]> {
    let params = new HttpParams();
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.providerId) {
      params = params.set('providerId', filter.providerId);
    }
    return this.http.get<SlotView[]>(`${API_BASE}/slots`, { params });
  }

  createSlot(model: CreateSlotModel): Observable<SlotView> {
    return this.http.post<SlotView>(`${API_BASE}/slots`, model);
  }

  cancelSlot(id: string, reason?: string): Observable<SlotView> {
    const body: UpdateSlotStatusModel = withoutBlanks({ status: 'CANCELLED' as SlotStatus, reason });
    return this.http.patch<SlotView>(`${API_BASE}/slots/${id}/status`, body);
  }

  appointments(status?: AppointmentStatus): Observable<AppointmentView[]> {
    const params = status ? new HttpParams().set('status', status) : new HttpParams();
    return this.http.get<AppointmentView[]>(`${API_BASE}/appointments`, { params });
  }

  bookAppointment(model: BookAppointmentModel): Observable<AppointmentView> {
    return this.http.post<AppointmentView>(`${API_BASE}/appointments`, withoutBlanks(model));
  }

  suggest(appointmentId: string): Observable<SuggestionView> {
    return this.http.post<SuggestionView>(`${API_BASE}/appointments/${appointmentId}/suggest`, null);
  }

  decide(suggestionId: string, model: SuggestionDecisionModel): Observable<SuggestionView> {
    return this.http.patch<SuggestionView>(`${API_BASE}/suggestions/${suggestionId}`, withoutBlanks(model));
  }
}
