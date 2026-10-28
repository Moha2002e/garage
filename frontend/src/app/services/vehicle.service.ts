import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Appointment } from '../models/appointment.model';
import { PageResponse } from '../models/page.model';
import { Vehicle, VehicleRequest } from '../models/vehicle.model';

@Injectable({ providedIn: 'root' })
export class VehicleService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/vehicles`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  /** Recherche, filtre actif et pagination sont faits par le back-end. */
  search(q: string, active: boolean | null, page: number, size: number): Observable<PageResponse<Vehicle>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    if (active !== null) {
      params = params.set('active', active);
    }
    return this.http.get<PageResponse<Vehicle>>(this.url, { params });
  }

  getById(id: string): Observable<Vehicle> {
    return this.http.get<Vehicle>(`${this.url}/${id}`);
  }

  /** Rendez-vous du véhicule, paginés par le back-end. */
  getAppointments(vehicleId: string, page: number, size: number): Observable<PageResponse<Appointment>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Appointment>>(`${this.url}/${vehicleId}/appointments`, { params });
  }

  // ******************************************
  // ************ POST ************
  // ******************************************

  create(request: VehicleRequest): Observable<Vehicle> {
    return this.http.post<Vehicle>(this.url, request);
  }

  // ******************************************
  // ************ UPDATE ************
  // ******************************************

  update(id: string, request: VehicleRequest): Observable<Vehicle> {
    return this.http.put<Vehicle>(`${this.url}/${id}`, request);
  }

  /** Réactivation (ADMIN), le back-end renvoie le véhicule à jour. */
  reactivate(id: string): Observable<Vehicle> {
    return this.http.patch<Vehicle>(`${this.url}/${id}/reactivate`, {});
  }

  // ******************************************
  // ************ DELETE ************
  // ******************************************

  /** Le back-end ne supprime jamais un véhicule, il le désactive (réponse 204 sans corps). */
  deactivate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
