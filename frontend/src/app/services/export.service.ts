import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

/** Exports Excel (ADMIN seulement, contrôlé par le back-end). */
@Injectable({ providedIn: 'root' })
export class ExportService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);
  private readonly url = `${environment.apiBaseUrl}/api/exports`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  clients(): Observable<Blob> {
    return this.download('clients.xls');
  }

  appointments(): Observable<Blob> {
    return this.download('appointments.xls');
  }

  // ******************************************
  // ************ PERSONNALISÉES ************
  // ******************************************

  private download(fileName: string): Observable<Blob> {
    return this.http.get(`${this.url}/${fileName}`, { responseType: 'blob' }).pipe(
      tap((blob) => this.save(blob, fileName))
    );
  }

  /** Déclenche le téléchargement dans le navigateur à partir du fichier reçu. */
  private save(blob: Blob, fileName: string): void {
    const objectUrl = URL.createObjectURL(blob);
    const link = this.document.createElement('a');
    link.href = objectUrl;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(objectUrl);
  }
}
