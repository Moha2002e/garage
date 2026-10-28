import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Quote, QuoteRequest } from '../models/quote.model';

/** Devis d'un rendez-vous : au plus un par rendez-vous. */
@Injectable({ providedIn: 'root' })
export class QuoteService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/appointments`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  /** Le back-end répond 204 sans corps s'il n'y a pas encore de devis : HttpClient donne alors null. */
  get(appointmentId: string): Observable<Quote | null> {
    return this.http.get<Quote | null>(`${this.url}/${appointmentId}/quote`);
  }

  // ******************************************
  // ************ UPDATE ************
  // ******************************************

  /** Crée ou remplace les lignes (devis brouillon seulement). */
  save(appointmentId: string, request: QuoteRequest): Observable<Quote> {
    return this.http.put<Quote>(`${this.url}/${appointmentId}/quote`, request);
  }

  /** Accepter ou refuser un devis brouillon. */
  changeStatus(appointmentId: string, status: 'ACCEPTED' | 'REFUSED'): Observable<Quote> {
    return this.http.patch<Quote>(`${this.url}/${appointmentId}/quote/status`, { status });
  }
}
