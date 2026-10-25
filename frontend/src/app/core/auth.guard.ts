import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models/auth.model';
import { AuthService } from '../services/auth.service';

/**
 * Pages privées : l'utilisateur est considéré connecté si GET /api/auth/me (cookie JWT HttpOnly)
 * a répondu au démarrage. Sinon on renvoie vers la connexion.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.user() ? true : inject(Router).createUrlTree(['/login']);
};

/** Pages d'accès (connexion, inscription) : un utilisateur déjà connecté va directement à son accueil. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.user() ? inject(Router).createUrlTree([auth.homePath()]) : true;
};

/** Racine du site : redirige selon le rôle (ou vers la connexion). */
export const homeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return router.createUrlTree([auth.user() ? auth.homePath() : '/login']);
};

/**
 * Accès par rôle. Une page non autorisée renvoie vers l'accueil (pas de page 403).
 * Ce contrôle améliore l'interface, le vrai contrôle reste celui du back-end.
 */
export const roleGuard = (...allowed: Role[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  const role = auth.role();
  return role && allowed.includes(role) ? true : inject(Router).createUrlTree(['/']);
};
