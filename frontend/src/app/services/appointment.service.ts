import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Appointment,
  AppointmentFilters,
  AppointmentRequest,
  AppointmentStatus
} from '../models/appointment.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class AppointmentService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/appointments`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  search(filters: AppointmentFilters, page: number, size: number): Observable<PageResponse<Appointment>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.q?.trim()) {
      params = params.set('q', filters.q.trim());
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    return this.http.get<PageResponse<Appointment>>(this.url, { params });
  }

  // ******************************************
  // ************ POST ************
  // ******************************************

  create(request: AppointmentRequest): Observable<Appointment> {
    return this.http.post<Appointment>(this.url, request);
  }

  // ******************************************
  // ************ UPDATE ************
  // ******************************************

  update(id: string, request: AppointmentRequest): Observable<Appointment> {
    return this.http.put<Appointment>(`${this.url}/${id}`, request);
  }

  /** Seul un rendez-vous planifié peut passer à terminé ou annulé. */
  changeStatus(id: string, status: AppointmentStatus): Observable<Appointment> {
    return this.http.patch<Appointment>(`${this.url}/${id}/status`, { status });
  }
}
