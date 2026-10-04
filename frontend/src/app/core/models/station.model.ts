export interface Station {
  id: number;
  name: string;
  region: string;
  address: string;
  latitude: number;
  longitude: number;
  status: string;
  managerName: string;
  managerId: number;
  tanks: Tank[];
  createdAt: string;
}

export interface CreateStationRequest {
  name: string;
  region: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  managerId?: number;
}

export interface UpdateStationRequest {
  name?: string;
  region?: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  managerId?: number;
}

export interface Tank {
  id: number;
  stationId: number;
  stationName: string;
  fuelTypeId: number;
  fuelTypeName: string;
  capacity: number;
  currentStock: number;
  criticalThreshold: number;
  stockPercentage: number;
  critical: boolean;
}
