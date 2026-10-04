export interface Sale {
  id: number;
  stationId: number;
  stationName: string;
  fuelTypeId: number;
  fuelTypeName: string;
  saleDate: string;
  quantity: number;
  validated: boolean;
  createdAt: string;
}

export interface CreateSaleRequest {
  stationId: number;
  fuelTypeId: number;
  saleDate: string;
  quantity: number;
}
