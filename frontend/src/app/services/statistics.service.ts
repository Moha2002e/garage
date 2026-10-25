import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { StatisticsResponse } from '../models/statistics.model';

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
}
