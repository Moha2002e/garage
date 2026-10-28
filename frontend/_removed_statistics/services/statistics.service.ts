import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { BrandCount, MonthRevenue, StatisticsResponse, StatusCount } from '../models/statistics.model';

@Injectable({ providedIn: 'root' })
export class StatisticsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/statistics`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  appointments(year: number): Observable<StatisticsResponse> {
    return this.http.get<StatisticsResponse>(`${this.url}/appointments`, {
      params: new HttpParams().set('year', year)
    });
  }

  statusDistribution(year: number): Observable<StatusCount[]> {
    return this.http.get<StatusCount[]>(`${this.url}/status-distribution`, {
      params: new HttpParams().set('year', year)
    });
  }

  revenueByMonth(year: number): Observable<MonthRevenue[]> {
    return this.http.get<MonthRevenue[]>(`${this.url}/revenue-by-month`, {
      params: new HttpParams().set('year', year)
    });
  }

  topBrands(limit = 5): Observable<BrandCount[]> {
    return this.http.get<BrandCount[]>(`${this.url}/top-brands`, {
      params: new HttpParams().set('limit', limit)
    });
  }
}
