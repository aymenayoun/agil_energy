export interface User {
  id: number;
  name: string;
  email: string;
  roleName: string;
  status: string;
  stationId?: number | null;
  stationName?: string | null;
  region?: string | null;
  createdAt: string;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  password: string;
  roleName: string;
  /** Required when roleName === 'STATION_MANAGER'. Backend enforces this rule. */
  stationId?: number | null;
  /** Required when roleName === 'MANAGER'. Backend enforces this rule. */
  region?: string | null;
}

export interface UpdateUserRequest {
  name?: string;
  email?: string;
  roleName?: string;
  stationId?: number | null;
  region?: string | null;
}
