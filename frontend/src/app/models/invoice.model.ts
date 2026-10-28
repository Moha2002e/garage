export type InvoiceStatus = 'ISSUED' | 'PAID';

/** Ligne de GET /api/invoices (page) et réponse de PUT /api/invoices/{id}/pay (avec lines). */
export interface Invoice {
  id: string;
  number: string;
  status: InvoiceStatus;
  appointmentId: string;
  clientId: string;
  clientName: string;
  vehicleLabel: string;
  // Instants ISO 8601.
  appointmentDate: string;
  total: number;
  issuedAt: string;
  paidAt: string | null;
}

export interface InvoiceLine {
  description: string;
  quantity: number;
  unitPrice: number;
  total: number;
}

/** Réponse de GET /api/invoices/{id}. */
export interface InvoiceDetail extends Invoice {
  lines: InvoiceLine[];
}

export interface InvoiceFilters {
  q?: string;
  status?: InvoiceStatus | null;
}

/** Exécution du batch de facturation (POST /api/invoices/batch/run et GET /api/invoices/batch/runs). */
export interface BatchRun {
  id: string | number;
  // Statut Spring Batch : COMPLETED, FAILED, STARTED, etc.
  status: string;
  readCount: number;
  writeCount: number;
  skipCount: number;
  errorCount: number;
  startTime: string;
  endTime: string | null;
}

export type BatchErrorCode =
  | 'NO_ACCEPTED_QUOTE'
  | 'EMPTY_QUOTE'
  | 'INVALID_AMOUNT'
  | 'DUPLICATE_INVOICE'
  | 'UNEXPECTED';

/** Réponse de GET /api/invoices/batch/errors. */
export interface BatchError {
  id: string | number;
  runId: string | number;
  appointmentId: string | null;
  appointmentLabel: string | null;
  // Code connu ou, par prudence, texte libre.
  code: BatchErrorCode | string;
  message: string;
  occurredAt: string;
}
