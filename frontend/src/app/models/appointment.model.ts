import { QuoteStatus } from './quote.model';

export type AppointmentStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface Appointment {
  id: string;
  date: string;
  // Durée en minutes (15 à 480, par pas de 15).
  durationMinutes: number;
  reason: string;
  status: AppointmentStatus;
  clientId: string;
  clientFirstName: string;
  clientLastName: string;
  vehicleId: string;
  plate: string;
  brand: string;
  model: string;
  // Résumé du devis, null tant qu'il n'y en a pas.
  quoteStatus: QuoteStatus | null;
  quoteTotal: number | null;
}

export interface AppointmentRequest {
  clientId: string;
  vehicleId: string;
  // Instant ISO 8601 (UTC).
  date: string;
  durationMinutes: number;
  reason: string;
}

export interface AppointmentFilters {
  q?: string;
  status?: AppointmentStatus | null;
  from?: string | null;
  to?: string | null;
}

/** Réponse de GET /api/appointments/calendar?from=&to= : une entrée par rendez-vous de la plage. */
export interface CalendarEvent {
  id: string;
  // Instants ISO 8601.
  start: string;
  end: string;
  title: string;
  status: AppointmentStatus;
  clientName: string;
  vehicleLabel: string;
  // Durée prévue en minutes.
  durationMinutes: number;
  // Avancement de 0 à 100, calculé par le back-end (utile surtout pour un rendez-vous en cours).
  progressPercent: number;
}
