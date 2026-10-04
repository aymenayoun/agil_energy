export interface LoginRequest {
  email: string;
  password: string;
}

export interface VerifyOtpRequest {
  otpToken: string;
  code: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken?: string;
  allDevices?: boolean;
}

export interface AuthResponse {
  token?: string;
  type?: string;
  userId?: number;
  name?: string;
  email?: string;
  role?: string;

  /** Station scope for STATION_MANAGER; null/undefined for other roles. */
  stationId?: number | null;
  stationName?: string | null;

  /** Region scope for MANAGER; null/undefined for other roles. */
  region?: string | null;

  refreshToken?: string;
  expiresIn?: number;
  refreshExpiresIn?: number;

  requiresOtp: boolean;
  otpToken?: string;
  otpExpiresInSeconds?: number;
}
