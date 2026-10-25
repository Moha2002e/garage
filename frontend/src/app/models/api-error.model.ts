import { HttpErrorResponse } from '@angular/common/http';

/** Corps d'erreur renvoyé par le back-end. */
export interface ApiError {
  timestamp: string;
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
}

/** Retourne le message du back-end s'il existe, sinon null (l'appelant affiche alors un texte générique). */
export function backendMessage(error: unknown): string | null {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body.message === 'string' && body.message.trim()) {
      return body.message;
    }
  }
  return null;
}
