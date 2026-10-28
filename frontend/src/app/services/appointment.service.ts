import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Appointment,
  AppointmentFilters,
  CalendarEvent,
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

  getById(id: string): Observable<Appointment> {
    return this.http.get<Appointment>(`${this.url}/${id}`);
  }

  /** Rendez-vous d'une plage de dates (from inclus, to exclu), pour le calendrier. */
  calendar(from: Date, to: Date): Observable<CalendarEvent[]> {
    const params = new HttpParams().set('from', from.toISOString()).set('to', to.toISOString());
    return this.http.get<CalendarEvent[]>(`${this.url}/calendar`, { params }).pipe(
      map((list) => list.map((e) => this.normalize(e))),
      // Si le back-end ne connaît pas encore l'endpoint, on se rabat sur la liste paginée.
      catchError((error: unknown) =>
        (error as { status?: number })?.status === 404 || (error as { status?: number })?.status === 405
          ? this.calendarFallback(from, to)
          : throwError(() => error)
      )
    );
  }

  /** Complète les champs de durée et d'avancement si une ancienne version du back-end ne les envoie pas. */
  private normalize(e: CalendarEvent): CalendarEvent {
    const minutes = Math.round((new Date(e.end).getTime() - new Date(e.start).getTime()) / 60000);
    const progress = Number.isFinite(e.progressPercent) ? e.progressPercent : 0;
    return {
      ...e,
      durationMinutes: Number.isFinite(e.durationMinutes) ? e.durationMinutes : Math.max(15, minutes),
      progressPercent: Math.min(100, Math.max(0, Math.round(progress)))
    };
  }

  private calendarFallback(from: Date, to: Date): Observable<CalendarEvent[]> {
    const day = (d: Date) =>
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const last = new Date(to.getTime() - 1);
    return this.search({ from: day(from), to: day(last) }, 0, 200).pipe(
      map((page) =>
        page.content.map((a) => ({
          id: a.id,
          start: a.date,
          end: new Date(new Date(a.date).getTime() + a.durationMinutes * 60000).toISOString(),
          title: a.reason,
          status: a.status,
          clientName: `${a.clientFirstName} ${a.clientLastName}`,
          vehicleLabel: `${a.brand} ${a.model}`,
          durationMinutes: a.durationMinutes,
          progressPercent: this.localProgress(a)
        }))
      )
    );
  }

  /** Avancement estimé quand le back-end ne le fournit pas. */
  private localProgress(a: Appointment): number {
    if (a.status === 'COMPLETED') {
      return 100;
    }
    if (a.status !== 'IN_PROGRESS' || a.durationMinutes <= 0) {
      return 0;
    }
    const elapsed = (Date.now() - new Date(a.date).getTime()) / 60000;
    return Math.min(100, Math.max(0, Math.round((elapsed / a.durationMinutes) * 100)));
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

  /** Déplace un rendez-vous planifié : le back-end refuse (400 ou 409) en cas de conflit, capacité ou statut. */
  rescheduleAppointment(id: string, date: string): Observable<Appointment> {
    return this.http.patch<Appointment>(`${this.url}/${id}/reschedule`, { date });
  }
}
