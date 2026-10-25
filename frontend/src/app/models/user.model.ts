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
