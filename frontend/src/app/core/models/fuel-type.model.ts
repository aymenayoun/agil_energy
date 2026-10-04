export interface FuelType {
  id: number;
  name: string;
  description?: string;
  createdAt: string;
}

export interface CreateFuelTypeRequest {
  name: string;
  description?: string;
}

export interface UpdateFuelTypeRequest {
  name?: string;
  description?: string;
}
