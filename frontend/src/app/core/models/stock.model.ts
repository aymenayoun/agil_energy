export interface StockMovement {
  id: number;
  tankId: number;
  fuelTypeName: string;
  stationName: string;
  movementType: string;
  quantity: number;
  stockBefore: number;
  stockAfter: number;
  justification: string;
  createdByName: string;
  createdAt: string;
}

export interface StockAdjustmentRequest {
  tankId: number;
  newStock: number;
  justification: string;
}
