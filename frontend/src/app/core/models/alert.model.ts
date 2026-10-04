export interface Alert {
  id: number;
  stationId: number;
  stationName: string;
  alertType: string;
  severity: string;
  message: string;
  status: string;
  resolvedByName: string;
  resolvedAt: string;
  createdAt: string;
}
