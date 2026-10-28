import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Dashboard } from '../models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/dashboard`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  get(): Observable<Dashboard> {
    return this.http.get<Dashboard>(this.url);
  }
}
