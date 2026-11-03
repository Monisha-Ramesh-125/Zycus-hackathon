import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'queue' },
  {
    path: 'queue',
    title: 'Reschedule queue · SlotMind',
    loadComponent: () => import('./pages/queue/queue-page.component').then(m => m.QueuePageComponent)
  },
  {
    path: 'appointments',
    title: 'Appointments · SlotMind',
    loadComponent: () => import('./pages/appointments/appointments-page.component').then(m => m.AppointmentsPageComponent)
  },
  {
    path: 'providers',
    title: 'Providers · SlotMind',
    loadComponent: () => import('./pages/providers/providers-page.component').then(m => m.ProvidersPageComponent)
  },
  {
    path: 'slots',
    title: 'Slots · SlotMind',
    loadComponent: () => import('./pages/slots/slots-page.component').then(m => m.SlotsPageComponent)
  },
  { path: '**', redirectTo: 'queue' }
];
