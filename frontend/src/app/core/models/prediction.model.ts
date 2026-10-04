export interface Prediction {
  id: number;
  stationId: number;
  stationName: string;
  fuelTypeId: number;
  fuelTypeName: string;
  predictionDate: string;
  predictedQuantity: number;
  modelName: string;
  confidenceScore: number;
  generatedAt: string;
}

export interface QuantileForecast {
  P10: number[];
  P50: number[];
  P90: number[];
  metrics: {
    mae: number;
    rmse: number;
    mape: number;
    quantile_loss_p50: number;
    coverage_80_pct: number;
    coverage_80_calibrated_pct?: number;
    calibration_offset?: number;
    interval_width_pct: number;
  };
}

export interface ShapFeature {
  feature: string;
  value: number | null;
  shap_value: number;
}

export interface ShapExplanation {
  base_value: number;
  top_features_per_day: ShapFeature[][];
  global_top_features: { feature: string; mean_abs_shap: number }[];
}

export interface IAResult {
  station_id: number;
  fuel_type_id: number;
  status: string;
  forecast_7_days: number[];
  anomaly_score: number;
  risk_level: 'LOW' | 'MEDIUM' | 'HIGH';
  days_before_rupture?: number;
  best_model?: string;
  models?: Record<string, { mae: number; rmse: number; mape: number }>;
  ensemble_weights?: Record<string, number>;
  feature_importance?: { feature: string; importance: number }[];
  quantiles?: QuantileForecast;
  shap?: ShapExplanation;
  message?: string;
}