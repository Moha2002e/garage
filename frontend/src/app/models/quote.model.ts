export type QuoteStatus = 'DRAFT' | 'ACCEPTED' | 'REFUSED';

export interface QuoteLine {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/** Réponse de GET/PUT /api/appointments/{id}/quote. */
export interface Quote {
  id: string;
  appointmentId: string;
  status: QuoteStatus;
  lines: QuoteLine[];
  total: number;
  updatedAt: string;
}

export interface QuoteLineRequest {
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface QuoteRequest {
  lines: QuoteLineRequest[];
}
