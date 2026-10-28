export interface MonthStat {
  month: number;
  planned: number;
  inProgress: number;
  completed: number;
  cancelled: number;
}

export interface StatisticsResponse {
  year: number;
  months: MonthStat[];
}

/** Réponse de GET /api/statistics/status-distribution?year= (un élément par statut présent). */
export interface StatusCount {
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  count: number;
}

/** Réponse de GET /api/statistics/revenue-by-month?year= (month de 1 à 12). */
export interface MonthRevenue {
  month: number;
  revenue: number;
}

/** Réponse de GET /api/statistics/top-brands?limit= */
export interface BrandCount {
  brand: string;
  count: number;
}
