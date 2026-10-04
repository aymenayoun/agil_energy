import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Prediction } from '../models';

export interface ModelMetric {
  model_name: string;
  model_version: string;
  station_id: number;
  fuel_type_id: number;
  mae: number;
  rmse: number;
  mape: number;
  created_at: string;
}

export interface ModelMetricsResponse {
  total: number;
  metrics: ModelMetric[];
}

export interface ShapFeature {
  feature: string;
  value: number | null;
  shap_value: number;
}

export interface ShapGlobalFeature {
  feature: string;
  mean_abs_shap: number;
}

export interface ShapExplanation {
  base_value: number;
  top_features_per_day: ShapFeature[][];   // one array per forecast day
  global_top_features: ShapGlobalFeature[];
}

export interface GeneratePredictionResult {
  status: string;
  forecast_7_days: number[];
  best_model?: string;
  ensemble_weights?: Record<string, number>;
  anomaly_score?: number;
  risk_level?: string;
  days_before_rupture?: number | null;
  shap?: ShapExplanation;
  quantiles?: {
    P10: number[];
    P50: number[];
    P90: number[];
    metrics?: Record<string, number>;
  };
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class PredictionService {
  private apiUrl = `${environment.apiUrl}/predictions`;

  constructor(private http: HttpClient) {}

  getPredictions(stationId: number, fuelTypeId: number): Observable<ApiResponse<Prediction[]>> {
    const params = new HttpParams().set('fuelTypeId', fuelTypeId.toString());
    return this.http.get<ApiResponse<Prediction[]>>(`${this.apiUrl}/${stationId}`, { params });
  }

  getLatestPredictions(stationId: number, fuelTypeId: number): Observable<ApiResponse<Prediction[]>> {
    const params = new HttpParams().set('fuelTypeId', fuelTypeId.toString());
    return this.http.get<ApiResponse<Prediction[]>>(`${this.apiUrl}/${stationId}/latest`, { params });
  }

  generatePrediction(stationId: number, fuelTypeId: number): Observable<ApiResponse<GeneratePredictionResult>> {
  return this.http.post<ApiResponse<GeneratePredictionResult>>(
    `${this.apiUrl}/generate`, { stationId, fuelTypeId });
}

  generateBatch(): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/generate/batch`, {});
  }

  checkIAHealth(): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.apiUrl}/ia/health`);
  }

  getModelMetrics(stationId?: number, fuelTypeId?: number): Observable<ApiResponse<ModelMetricsResponse>> {
    let params = new HttpParams();
    if (stationId)   params = params.set('stationId', stationId.toString());
    if (fuelTypeId)  params = params.set('fuelTypeId', fuelTypeId.toString());
    return this.http.get<ApiResponse<ModelMetricsResponse>>(`${this.apiUrl}/model-metrics`, { params });
  }

  // ===== Region-based predictions =====

  getRegions(): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/regions`);
  }

  getRegionFuelTypes(region: string): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.apiUrl}/regions/${encodeURIComponent(region)}/fuel-types`);
  }

  generateRegionPrediction(region: string, fuelTypeId: number): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/generate/region`, { region, fuelTypeId });
  }
}
