export interface Client {
  id: string;
  lastName: string;
  firstName: string;
  email: string;
  phone: string | null;
  active: boolean;
}

export interface ClientRequest {
  lastName: string;
  firstName: string;
  email: string;
  phone: string | null;
}

export interface Vehicle {
  id: string;
  clientId: string;
  plate: string;
  brand: string;
  model: string;
}

export interface VehicleRequest {
  clientId: string;
  plate: string;
  brand: string;
  model: string;
}
