export interface MonthStat {
  month: number;
  planned: number;
  completed: number;
  cancelled: number;
}

export interface StatisticsResponse {
  year: number;
  months: MonthStat[];
}
