import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  CurrentUser,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  Role
} from '../models/auth.model';
import { SettingsService } from './settings.service';

/**
 * Session : le JWT est dans un cookie HttpOnly posé par le back-end, le front ne le voit jamais.
 * On sait donc si on est connecté uniquement en interrogeant GET /api/auth/me.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly settings = inject(SettingsService);
  private readonly url = `${environment.apiBaseUrl}/api/auth`;

  readonly user = signal<CurrentUser | null>(null);
  readonly role = computed<Role | null>(() => this.user()?.role ?? null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  // ADMIN et EMPLOYEE peuvent créer et modifier ; le CLIENT est en lecture seule.
  readonly canWrite = computed(() => this.role() === 'ADMIN' || this.role() === 'EMPLOYEE');

  // ******************************************
  // ************ GET ************
  // ******************************************

  /** Au démarrage : cookie valide = utilisateur chargé ; 401 ou erreur = pas de session. */
  loadSession(): Observable<CurrentUser | null> {
    return this.http.get<CurrentUser>(`${this.url}/me`).pipe(
      tap((user) => this.user.set(user)),
      catchError(() => {
        this.user.set(null);
        return of(null);
      })
    );
  }

  // ******************************************
  // ************ POST ************
  // ******************************************

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.http.post<CurrentUser>(`${this.url}/login`, request).pipe(
      tap((user) => {
        this.user.set(user);
        this.settings.applyAccountLanguage(user.language);
      })
    );
  }

  register(request: RegisterRequest): Observable<void> {
    return this.http.post<void>(`${this.url}/register`, request);
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.url}/forgot-password`, { email });
  }

  resetPassword(request: ResetPasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.url}/reset-password`, request);
  }

  /** Le back-end efface le cookie ; côté front on vide seulement l'état en mémoire. */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.url}/logout`, {}).pipe(tap(() => this.user.set(null)));
  }

  // ******************************************
  // ************ PERSONNALISÉES ************
  // ******************************************

  /** Utilisé par l'intercepteur sur un 401 : le cookie a expiré ou n'est plus valide. */
  clearSession(): void {
    this.user.set(null);
  }

  /** Page d'accueil selon le rôle : le CLIENT ne voit que ses rendez-vous. */
  homePath(): string {
    return this.role() === 'CLIENT' ? '/appointments' : '/clients';
  }
}
