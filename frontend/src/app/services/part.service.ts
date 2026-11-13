import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PageResponse } from '../models/page.model';
import { Part, PartRequest, StockAdjustRequest, StockMovement } from '../models/part.model';

export interface PartFilters {
  q: string;
  active: boolean | null;
  lowStock: boolean;
}

@Injectable({ providedIn: 'root' })
export class PartService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/parts`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  /** Recherche, filtres et pagination sont faits par le back-end. */
  search(filters: PartFilters, page: number, size: number): Observable<PageResponse<Part>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.q.trim()) {
      params = params.set('q', filters.q.trim());
    }
    if (filters.active !== null) {
      params = params.set('active', filters.active);
    }
    if (filters.lowStock) {
      params = params.set('lowStock', true);
    }
    return this.http.get<PageResponse<Part>>(this.url, { params });
  }

  /** Journal des mouvements d'une pièce, du plus récent au plus ancien. */
  movements(id: string, page: number, size: number): Observable<PageResponse<StockMovement>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<StockMovement>>(`${this.url}/${id}/movements`, { params });
  }

  // ******************************************
  // ************ POST ************
  // ******************************************

  create(request: PartRequest): Observable<Part> {
    return this.http.post<Part>(this.url, request);
  }

  /** Ajoute, retire ou corrige du stock ; le back-end renvoie la pièce mise à jour. */
  adjustStock(id: string, request: StockAdjustRequest): Observable<Part> {
    return this.http.post<Part>(`${this.url}/${id}/stock`, request);
  }

  // ******************************************
  // ************ UPDATE ************
  // ******************************************

  update(id: string, request: PartRequest): Observable<Part> {
    return this.http.put<Part>(`${this.url}/${id}`, request);
  }

  // ******************************************
  // ************ DELETE ************
  // ******************************************

  /** Le back-end ne supprime jamais une pièce, il la désactive. */
  deactivate(id: string): Observable<Part> {
    return this.http.delete<Part>(`${this.url}/${id}`);
  }

  // ******************************************
  // ************ PERSONNALISÉES ************
  // ******************************************

  activate(id: string): Observable<Part> {
    return this.http.post<Part>(`${this.url}/${id}/activate`, {});
  }
}
