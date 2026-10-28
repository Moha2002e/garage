import { Language, Role } from './auth.model';

export interface UserAccount {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  language: Language;
  enabled: boolean;
  clientId: string | null;
}

/** Corps de POST /api/users (création d'un compte par un administrateur). */
export interface UserCreateRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  role: Role;
}

/** Corps de PUT /api/users/{id} : nom, rôle et activation du compte. */
export interface UserUpdateRequest {
  firstName: string;
  lastName: string;
  role: Role;
  enabled: boolean;
}
