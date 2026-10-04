import { Tank } from './station.model';
import { Alert } from './alert.model';

export interface Dashboard {
  totalStations: number;
  activeStations: number;
  activeAlerts: number;
  criticalAlerts: number;
  criticalTanks: Tank[];
  recentAlerts: Alert[];
  stationSummaries: StationSummary[];
}

export interface StationSummary {
  stationId: number;
  stationName: string;
  region: string;
  avgDailyConsumption: number;
  daysBeforeRupture: number;
  activeAlertCount: number;
}
