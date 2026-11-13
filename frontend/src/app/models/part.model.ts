export type StockMovementType = 'IN' | 'OUT' | 'ADJUSTMENT';

export interface Part {
  id: string;
  reference: string;
  name: string;
  unitPrice: number;
  quantityInStock: number;
  lowStockThreshold: number;
  // Délai de livraison du fournisseur en jours (0 = inconnu).
  leadTimeDays: number;
  // Calculé par le back-end : stock au seuil d'alerte ou en dessous.
  lowStock: boolean;
  active: boolean;
  createdAt?: string;
}

/** Création et modification. Le stock n'est jamais envoyé ici : il ne change que par un mouvement. */
export interface PartRequest {
  reference: string;
  name: string;
  unitPrice: number;
  lowStockThreshold: number;
  leadTimeDays: number;
}

/** IN et OUT : quantité strictement positive. ADJUSTMENT : différence signée, jamais nulle. */
export interface StockAdjustRequest {
  type: StockMovementType;
  quantity: number;
  reason: string | null;
}

export interface StockMovement {
  id: string;
  type: StockMovementType;
  quantity: number;
  reason: string | null;
  createdAt: string;
}
