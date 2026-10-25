export type Role = 'ADMIN' | 'EMPLOYEE' | 'CLIENT';
export type Language = 'FR' | 'EN';

/** Réponse de GET /api/auth/me et de POST /api/auth/login. */
export interface CurrentUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  language: Language;
  clientId: string | null;
  // Vrai quand le compte CLIENT est rattaché à une fiche client (sinon il ne voit aucun rendez-vous).
  clientLinked: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
  rememberMe: boolean;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  language: Language;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}
