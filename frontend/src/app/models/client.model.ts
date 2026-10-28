export interface Client {
  id: string;
  lastName: string;
  firstName: string;
  email: string;
  phone: string | null;
  active: boolean;
  createdAt?: string;
}

export interface ClientRequest {
  lastName: string;
  firstName: string;
  email: string;
  phone: string | null;
}
