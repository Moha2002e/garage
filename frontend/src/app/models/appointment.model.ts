export type AppointmentStatus = 'PLANNED' | 'COMPLETED' | 'CANCELLED';

export interface Appointment {
  id: string;
  date: string;
  reason: string;
  status: AppointmentStatus;
  clientId: string;
  clientFirstName: string;
  clientLastName: string;
  vehicleId: string;
  plate: string;
  brand: string;
  model: string;
}

export interface AppointmentRequest {
  clientId: string;
  vehicleId: string;
  // Instant ISO 8601 (UTC).
  date: string;
  reason: string;
}

export interface AppointmentFilters {
  q?: string;
  status?: AppointmentStatus | null;
  from?: string | null;
  to?: string | null;
}
