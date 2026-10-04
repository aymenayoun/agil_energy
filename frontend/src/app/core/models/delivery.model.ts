export interface Delivery {
  id: number;
  stationId: number;
  stationName: string;
  fuelTypeId: number;
  fuelTypeName: string;
  deliveryDate: string;
  quantity: number;
  validated: boolean;
  createdByName: string;
  createdAt: string;
}

export interface CreateDeliveryRequest {
  stationId: number;
  fuelTypeId: number;
  deliveryDate: string;
  quantity: number;
}
