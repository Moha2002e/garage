import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpXsrfTokenExtractor
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS'];

/**
 * Intercepteur des appels vers l'API.
 * 1. Cookie : le JWT est dans un cookie HttpOnly posé par le back-end. Le navigateur ne l'envoie
 *    vers une autre origine (4200 vers 8080) que si withCredentials est actif.
 * 2. CSRF : le back-end pose un cookie XSRF-TOKEN lisible et attend sa valeur dans X-XSRF-TOKEN
 *    pour toute requête qui modifie des données. Angular ne le fait pas seul pour une URL
 *    absolue d'une autre origine, donc on l'ajoute ici.
 * 3. Erreurs : réaction commune pour que chaque page n'ait pas à les gérer une par une.
 */
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  // On ne touche pas aux autres requêtes (fichiers de traduction, etc.).
  if (!req.url.startsWith(environment.apiBaseUrl)) {
    return next(req);
  }

  const xsrfToken = inject(HttpXsrfTokenExtractor).getToken();
  const needsCsrf = !SAFE_METHODS.includes(req.method) && xsrfToken;
  const apiRequest = req.clone({
    withCredentials: true,
    ...(needsCsrf ? { setHeaders: { 'X-XSRF-TOKEN': xsrfToken } } : {})
  });

  const auth = inject(AuthService);
  const router = inject(Router);
  const notifications = inject(NotificationService);

  return next(apiRequest).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        // Un 401 sur login ou me est normal (mauvais identifiants, pas encore de session) : la page s'en charge.
        const isAuthProbe = /\/api\/auth\/(login|me)$/.test(req.url);
        if (error.status === 401 && !isAuthProbe) {
          // Token expiré ou invalide : on oublie l'utilisateur et on retourne à la connexion.
          auth.clearSession();
          router.navigateByUrl('/login');
        } else if (error.status === 403) {
          // Pas de page « accès refusé » : retour à l'accueil.
          router.navigateByUrl('/');
        } else if (error.status === 0 || error.status >= 500) {
          // Serveur injoignable ou panne : message clair en toast.
          notifications.error(error);
        }
      }
      return throwError(() => error);
    })
  );
};
