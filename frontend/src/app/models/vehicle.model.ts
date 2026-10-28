/** Réponse de l'API véhicules (VehicleResponse). */
export interface Vehicle {
  id: string;
  clientId: string;
  clientFirstName: string;
  clientLastName: string;
  plate: string;
  brand: string;
  model: string;
  // Un véhicule désactivé n'est jamais supprimé physiquement.
  active: boolean;
}

export interface VehicleRequest {
  clientId: string;
  plate: string;
  brand: string;
  model: string;
}
