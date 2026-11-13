export type QuoteStatus = 'DRAFT' | 'ACCEPTED' | 'REFUSED';

export interface QuoteLine {
  id: string;
  // Renseignés quand la ligne est une pièce du catalogue ; vides pour la main-d'œuvre.
  partId: string | null;
  partReference: string | null;
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
  // Une ligne pièce réserve du stock (quantité entière) ; sans pièce, c'est de la main-d'œuvre.
  partId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface QuoteRequest {
  lines: QuoteLineRequest[];
}
