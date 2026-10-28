import { Appointment, AppointmentStatus } from './appointment.model';

/** Rendez-vous résumé des blocs de l'accueil. */
export interface DashboardAppointment {
  id: string;
  // Instants ISO 8601.
  start: string;
  end: string;
  status: AppointmentStatus;
  clientName: string;
  vehicleLabel: string;
  // Avancement de 0 à 100, si le back-end le fournit.
  progressPercent?: number | null;
}

/** Réponse de GET /api/dashboard. */
export interface Dashboard {
  today: Appointment[];
  plannedNext7Days: number;
  inProgress: number;
  activeClients: number;
  activeVehicles: number;
  monthCompleted: number;
  monthRevenue: number;
  todayAppointments: DashboardAppointment[];
  inProgressNow: DashboardAppointment[];
  upcoming: DashboardAppointment[];
  unpaidInvoicesCount: number;
  unpaidInvoicesAmount: number;
  pendingInvoiceCount: number;
}
