import { AppointmentStatus } from '../models/appointment.model';
import { InvoiceStatus } from '../models/invoice.model';

export type TagSeverity = 'info' | 'warn' | 'success' | 'danger' | 'secondary';

/** Couleur d'un statut de rendez-vous : bleu, orange, vert, rouge. Utilisée partout où un statut s'affiche. */
export function appointmentSeverity(status: AppointmentStatus): TagSeverity {
  switch (status) {
    case 'PLANNED':
      return 'info';
    case 'IN_PROGRESS':
      return 'warn';
    case 'COMPLETED':
      return 'success';
    default:
      return 'danger';
  }
}

/** Facture : émise en orange, payée en vert. */
export function invoiceSeverity(status: InvoiceStatus): TagSeverity {
  return status === 'PAID' ? 'success' : 'warn';
}

/** Exécution du batch : terminée en vert, échec en rouge, en cours en orange, le reste en gris. */
export function batchSeverity(status: string): TagSeverity {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'STARTED':
    case 'STARTING':
      return 'warn';
    default:
      return 'secondary';
  }
}
