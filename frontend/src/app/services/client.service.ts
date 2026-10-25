import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Client, ClientRequest, Vehicle, VehicleRequest } from '../models/client.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/clients`;
  private readonly vehiclesUrl = `${environment.apiBaseUrl}/api/vehicles`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  /** Recherche, filtre et pagination sont faits par le back-end. */
  search(q: string, page: number, size: number, active?: boolean): Observable<PageResponse<Client>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    if (active !== undefined) {
      params = params.set('active', active);
    }
    return this.http.get<PageResponse<Client>>(this.url, { params });
  }

  getById(id: string): Observable<Client> {
    return this.http.get<Client>(`${this.url}/${id}`);
  }

  getVehicles(clientId: string): Observable<Vehicle[]> {
    return this.http.get<Vehicle[]>(`${this.url}/${clientId}/vehicles`);
  }

  // ******************************************
  // ************ POST ************
  // ******************************************

  create(request: ClientRequest): Observable<Client> {
    return this.http.post<Client>(this.url, request);
  }

  createVehicle(request: VehicleRequest): Observable<Vehicle> {
    return this.http.post<Vehicle>(this.vehiclesUrl, request);
  }

  // ******************************************
  // ************ UPDATE ************
  // ******************************************

  update(id: string, request: ClientRequest): Observable<Client> {
    return this.http.put<Client>(`${this.url}/${id}`, request);
  }

  // ******************************************
  // ************ DELETE ************
  // ******************************************

  /** Le back-end ne supprime jamais un client, il le désactive. */
  deactivate(id: string): Observable<Client> {
    return this.http.delete<Client>(`${this.url}/${id}`);
  }

  // ******************************************
  // ************ PERSONNALISÉES ************
  // ******************************************

  activate(id: string): Observable<Client> {
    return this.http.post<Client>(`${this.url}/${id}/activate`, {});
  }
}
