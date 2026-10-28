import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { BatchError, BatchRun, Invoice, InvoiceDetail, InvoiceFilters } from '../models/invoice.model';
import { PageResponse } from '../models/page.model';

@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/invoices`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  search(filters: InvoiceFilters, page: number, size: number): Observable<PageResponse<Invoice>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.q?.trim()) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<PageResponse<Invoice>>(this.url, { params });
  }

  get(id: string): Observable<InvoiceDetail> {
    return this.http.get<InvoiceDetail>(`${this.url}/${id}`);
  }

  runs(page: number, size: number): Observable<PageResponse<BatchRun>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<BatchRun>>(`${this.url}/batch/runs`, { params });
  }

  errors(page: number, size: number, runId?: string | number | null): Observable<PageResponse<BatchError>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (runId !== null && runId !== undefined && runId !== '') {
      params = params.set('runId', String(runId));
    }
    return this.http.get<PageResponse<BatchError>>(`${this.url}/batch/errors`, { params });
  }

  /** PDF de la facture (application/pdf). Une erreur est rendue avec son corps JSON déjà lu. */
  pdf(id: string): Observable<Blob> {
    return this.http.get(`${this.url}/${id}/pdf`, { responseType: 'blob' }).pipe(
      catchError((error: unknown) => this.readBlobError(error))
    );
  }

  /** Avec responseType blob, le corps d'erreur est un Blob : on le lit en JSON pour que le message du back-end reste utilisable. */
  private readBlobError(error: unknown): Observable<never> {
    if (!(error instanceof HttpErrorResponse) || !(error.error instanceof Blob)) {
      return throwError(() => error);
    }
    return from(error.error.text().catch(() => '')).pipe(
      switchMap((text) => {
        let body: unknown = null;
        try {
          body = text ? JSON.parse(text) : null;
        } catch {
          body = null;
        }
        return throwError(
          () =>
            new HttpErrorResponse({
              error: body,
              headers: error.headers,
              status: error.status,
              statusText: error.statusText,
              url: error.url ?? undefined
            })
        );
      })
    );
  }

  // ******************************************
  // ************ CREATE ************
  // ******************************************

  /** Lance le batch de facturation et rend le résumé de l'exécution. */
  runBatch(): Observable<BatchRun> {
    return this.http.post<BatchRun>(`${this.url}/batch/run`, {});
  }

  // ******************************************
  // ************ UPDATE ************
  // ******************************************

  /** 409 si la facture est déjà payée. */
  pay(id: string): Observable<InvoiceDetail> {
    return this.http.put<InvoiceDetail>(`${this.url}/${id}/pay`, {});
  }
}
