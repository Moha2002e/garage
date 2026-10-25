import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MessageService } from 'primeng/api';
import { backendMessage } from '../models/api-error.model';

/** Toasts affichés en haut à droite (le p-toast global est dans le composant racine). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly messages = inject(MessageService);
  private readonly translate = inject(TranslateService);

  // ******************************************
  // ************ PERSONNALISÉES ************
  // ******************************************

  success(key: string, params?: Record<string, string>): void {
    this.messages.add({ severity: 'success', summary: this.translate.instant(key, params), life: 4000 });
  }

  /** Affiche le message du back-end s'il existe, sinon un texte générique. */
  error(error: unknown): void {
    this.messages.add({ severity: 'error', summary: this.errorText(error), life: 6000 });
  }

  errorText(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return this.translate.instant('errors.network');
    }
    return backendMessage(error) ?? this.translate.instant('errors.generic');
  }
}
