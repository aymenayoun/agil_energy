import os
import numpy as np
import pandas as pd
import joblib
from datetime import datetime
from sklearn.linear_model import LinearRegression
from sklearn.ensemble import RandomForestRegressor, IsolationForest
from sklearn.metrics import mean_absolute_error, mean_squared_error
from prophet import Prophet
from xgboost import XGBRegressor

from app.config.settings import MODEL_DIR, ML_CONFIG

import warnings
import logging
warnings.filterwarnings("ignore", message=".*plotly.*")
logging.getLogger("shap").setLevel(logging.ERROR)

# ==================================================
# Skip retraining if possible : on vérifie la fraîcheur du modèle avant de lancer un nouvel entraînement.
# ==================================================
def get_latest_model_path(station_id: int, fuel_type_id: int, prefix: str) -> str | None:
    """
    Return the path of the most recently saved model for this
    (station_id, fuel_type_id, model_type) combo, or None if not found.
    """
    import glob
    pattern = os.path.join(MODEL_DIR, f"{prefix}_{station_id}_{fuel_type_id}_*.pkl")
    files = sorted(glob.glob(pattern))
    return files[-1] if files else None


def is_model_fresh(model_path: str, max_age_hours: int = 24) -> bool:
    """Return True if the model file was saved less than max_age_hours ago."""
    if not model_path or not os.path.exists(model_path):
        return False
    age_seconds = (datetime.now() - datetime.fromtimestamp(os.path.getmtime(model_path))).total_seconds()
    return age_seconds < max_age_hours * 3600

# ==================================================
# MÉTRIQUES D'ÉVALUATION
# ==================================================

def calculate_metrics(y_true, y_pred) -> dict:
    """Calcul MAE, RMSE et MAPE conformément au cahier des charges."""
    y_true = np.array(y_true, dtype=float)
    y_pred = np.array(y_pred, dtype=float)

    mae = mean_absolute_error(y_true, y_pred)
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))

    # MAPE : éviter la division par zéro
    mask = y_true != 0
    if mask.sum() > 0:
        mape = np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask]))
    else:
        mape = 0.0

    return {"mae": float(mae), "rmse": float(rmse), "mape": float(mape)}


# ==================================================
# 1. RÉGRESSION LINÉAIRE (Baseline)
# ==================================================

def train_linear_regression(X_train, y_train, X_test, y_test, station_id: int, fuel_type_id: int) -> dict:
    latest = get_latest_model_path(station_id, fuel_type_id, "lr")
    if is_model_fresh(latest):
        model = joblib.load(latest)
        y_pred = np.maximum(model.predict(X_test), 0)
        metrics = calculate_metrics(y_test, y_pred)
        return {
            "model": model,
            "model_name": "LinearRegression",
            "version": os.path.basename(latest).replace(".pkl", "").split("_")[-2] + "_" + os.path.basename(latest).replace(".pkl", "").split("_")[-1],
            "metrics": metrics,
            "filepath": latest,
            "from_cache": True
        }

    model = LinearRegression()
    model.fit(X_train, y_train)
    y_pred = np.maximum(model.predict(X_test), 0)
    metrics = calculate_metrics(y_test, y_pred)

    os.makedirs(MODEL_DIR, exist_ok=True)
    version = datetime.now().strftime("%Y%m%d_%H%M%S")
    filepath = os.path.join(MODEL_DIR, f"lr_{station_id}_{fuel_type_id}_{version}.pkl")
    joblib.dump(model, filepath)

    return {
        "model": model,
        "model_name": "LinearRegression",
        "version": version,
        "metrics": metrics,
        "filepath": filepath,
        "from_cache": False
    }


# ==================================================
# 2. RANDOM FOREST REGRESSOR
# ==================================================

def train_random_forest(X_train, y_train, X_test, y_test, station_id: int, fuel_type_id: int) -> dict:
    latest = get_latest_model_path(station_id, fuel_type_id, "rf")
    if is_model_fresh(latest):
        model = joblib.load(latest)
        y_pred = np.maximum(model.predict(X_test), 0)
        metrics = calculate_metrics(y_test, y_pred)
        return {
            "model": model,
            "model_name": "RandomForest",
            "version": os.path.basename(latest).replace(".pkl", "").split("_")[-2] + "_" + os.path.basename(latest).replace(".pkl", "").split("_")[-1],
            "metrics": metrics,
            "filepath": latest,
            "from_cache": True
        }

    model = RandomForestRegressor(
        n_estimators=100,
        max_depth=10,
        min_samples_split=5,
        min_samples_leaf=3,
        random_state=42,
        n_jobs=-1
    )
    model.fit(X_train, y_train)
    y_pred = np.maximum(model.predict(X_test), 0)
    metrics = calculate_metrics(y_test, y_pred)

    os.makedirs(MODEL_DIR, exist_ok=True)
    version = datetime.now().strftime("%Y%m%d_%H%M%S")
    filepath = os.path.join(MODEL_DIR, f"rf_{station_id}_{fuel_type_id}_{version}.pkl")
    joblib.dump(model, filepath)

    return {
        "model": model,
        "model_name": "RandomForest",
        "version": version,
        "metrics": metrics,
        "filepath": filepath,
        "from_cache": False
    }

# ==================================================
# 2B. XGBOOST REGRESSOR
# ==================================================

def train_xgboost(X_train, y_train, X_test, y_test, station_id: int, fuel_type_id: int) -> dict:
    latest = get_latest_model_path(station_id, fuel_type_id, "xgb")
    if is_model_fresh(latest):
        model = joblib.load(latest)
        y_pred = np.maximum(model.predict(X_test), 0)
        metrics = calculate_metrics(y_test, y_pred)
        return {
            "model": model,
            "model_name": "XGBoost",
            "version": os.path.basename(latest).replace(".pkl", "").split("_")[-2] + "_" + os.path.basename(latest).replace(".pkl", "").split("_")[-1],
            "metrics": metrics,
            "filepath": latest,
            "from_cache": True
        }

    model = XGBRegressor(
        n_estimators=500,           # was 200 — set higher, early stopping will cut it
        max_depth=4,                # was 6 — shallower trees generalize better
        learning_rate=0.05,         # was 0.08 — slower learning with early stopping
        subsample=0.7,              # was 0.8 — more randomness
        colsample_bytree=0.7,      # was 0.8 — force feature diversity
        min_child_weight=5,         # was 3 — fewer splits on small data
        reg_alpha=0.5,              # was 0.1 — stronger L1 regularization
        reg_lambda=3.0,             # was 1.0 — stronger L2 regularization
        random_state=42,
        n_jobs=-1,
        verbosity=0
    )
    model.fit(
        X_train, y_train,
        eval_set=[(X_test, y_test)],
        verbose=False
    )
    # Apply early stopping manually: use the iteration with best validation score
    if hasattr(model, 'best_iteration') and model.best_iteration > 0:
        model.set_params(n_estimators=model.best_iteration)
        model.fit(X_train, y_train, verbose=False)

    y_pred = np.maximum(model.predict(X_test), 0)
    metrics = calculate_metrics(y_test, y_pred)

    os.makedirs(MODEL_DIR, exist_ok=True)
    version = datetime.now().strftime("%Y%m%d_%H%M%S")
    filepath = os.path.join(MODEL_DIR, f"xgb_{station_id}_{fuel_type_id}_{version}.pkl")
    joblib.dump(model, filepath)

    return {
        "model": model,
        "model_name": "XGBoost",
        "version": version,
        "metrics": metrics,
        "filepath": filepath,
        "from_cache": False
    }

# ==================================================
# 2C. QUANTILE XGBOOST (P10 / P50 / P90 prediction intervals)
# ==================================================

def train_xgboost_quantiles(X_train, y_train, X_test, y_test,
                            station_id: int, fuel_type_id: int,
                            quantiles: tuple = (0.1, 0.5, 0.9)) -> dict:
    """
    Train 3 XGBoost models, one per quantile (10th, 50th, 90th).
    Uses XGBoost's quantile objective (requires XGBoost >= 1.7).
    Produces prediction intervals rather than just a point forecast.
    
    Returns a dict with:
    - models: {q: XGBRegressor instance, ...}
    - metrics: {quantile_loss, coverage_80, interval_width_avg}
    - filepath, version, model_name
    """
    os.makedirs(MODEL_DIR, exist_ok=True)

    models = {}
    predictions = {}
    for q in quantiles:
        model = XGBRegressor(
            objective="reg:quantileerror",
            quantile_alpha=q,
            n_estimators=400,
            max_depth=5,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            min_child_weight=2,
            reg_alpha=0.1,
            reg_lambda=1.0,
            random_state=42,
            n_jobs=-1,
            verbosity=0,
        )
        model.fit(X_train, y_train, eval_set=[(X_test, y_test)], verbose=False)
        y_pred = np.maximum(model.predict(X_test), 0)
        models[q] = model
        predictions[q] = y_pred

    # ---- Metrics ----
    y_true = np.array(y_test)
    p10 = predictions[quantiles[0]]
    p50 = predictions[quantiles[1]]
    p90 = predictions[quantiles[-1]]

    # Coverage: fraction of true values that fall inside [P10, P90]
    inside = (y_true >= p10) & (y_true <= p90)
    coverage_80 = float(inside.mean())

    # Average interval width (as % of mean prediction)
    interval_width_pct = float(
        np.mean((p90 - p10) / np.where(p50 > 0, p50, 1)) * 100
    )

    # Quantile loss (aka "pinball loss") for the median
    residuals = y_true - p50
    quantile_loss = float(np.mean(np.maximum(0.5 * residuals, (0.5 - 1) * residuals)))

    # Point-forecast metrics on the median for comparability
    point_metrics = calculate_metrics(y_true, p50)

    # ---- Conformal calibration: widen intervals if coverage is below target ----
    target_coverage = 0.80
    if coverage_80 < target_coverage - 0.05 and len(y_true) >= 10:
        # Compute per-point max of (how far below P10) and (how far above P90)
        deficit_low = np.maximum(p10 - y_true, 0)
        deficit_high = np.maximum(y_true - p90, 0)
        residuals_abs = np.maximum(deficit_low, deficit_high)
        alpha = 1 - target_coverage
        calibration_offset = float(np.quantile(residuals_abs, 1 - alpha))
    else:
        calibration_offset = 0.0

    # Recompute coverage AFTER conformal widening (reported for transparency)
    if calibration_offset > 0:
        p10_cal = np.maximum(p10 - calibration_offset, 0)
        p90_cal = p90 + calibration_offset
        inside_cal = (y_true >= p10_cal) & (y_true <= p90_cal)
        coverage_80_calibrated = float(inside_cal.mean())
    else:
        coverage_80_calibrated = coverage_80

    metrics = {
        "mae": float(point_metrics["mae"]),
        "rmse": float(point_metrics["rmse"]),
        "mape": float(point_metrics["mape"]),
        "quantile_loss_p50": float(round(quantile_loss, 4)),
        "coverage_80_pct": float(round(coverage_80 * 100, 2)),                # raw
        "coverage_80_calibrated_pct": float(round(coverage_80_calibrated * 100, 2)),  # after conformal
        "calibration_offset": float(round(calibration_offset, 2)),
        "interval_width_pct": float(round(interval_width_pct, 2)),
    }

    # Persist
    version = datetime.now().strftime("%Y%m%d_%H%M%S")
    filepath = os.path.join(
        MODEL_DIR, f"xgbq_{station_id}_{fuel_type_id}_{version}.pkl"
    )
    joblib.dump({
        "quantiles": quantiles,
        "models": models,
        "calibration_offset": calibration_offset,
    }, filepath)

    return {
        "model_name": "XGBoostQuantile",
        "version": version,
        "models": models,
        "quantiles": list(quantiles),
        "metrics": metrics,
        "filepath": filepath,
        "from_cache": False,
        "calibration_offset": calibration_offset,
    }


def predict_quantiles(
    quantile_models: dict,
    X_future,
    calibration_offset: float = 0.0,
) -> dict:
    """
    Predict P10, P50, P90 for future horizon, with optional conformal calibration widening.
    """
    out = {}
    for q, model in quantile_models.items():
        preds = np.maximum(model.predict(X_future), 0)
        out[q] = preds

    # Apply conformal calibration: widen P10 down and P90 up symmetrically
    if calibration_offset > 0 and len(out) >= 2:
        qs = sorted(out.keys())
        out[qs[0]] = np.maximum(out[qs[0]] - calibration_offset, 0)
        out[qs[-1]] = out[qs[-1]] + calibration_offset

    # Enforce monotonicity: P10 <= P50 <= P90
    qs = sorted(out.keys())
    for i in range(len(qs) - 1):
        lower = out[qs[i]]
        upper = out[qs[i + 1]]
        mask = lower > upper
        if mask.any():
            lower[mask], upper[mask] = upper[mask], lower[mask]
            out[qs[i]] = lower
            out[qs[i + 1]] = upper

    return out

# ==================================================
# FEATURE IMPORTANCE
# ==================================================

def get_feature_importance(model, feature_names: list, top_n: int = 15) -> list:
    """
    Extract feature importance from tree-based models (RF, XGBoost).
    Returns sorted list of {feature, importance} dicts.
    """
    try:
        if hasattr(model, 'feature_importances_'):
            importances = model.feature_importances_
        else:
            return []

        paired = list(zip(feature_names, importances))
        paired.sort(key=lambda x: x[1], reverse=True)

        return [
            {"feature": name, "importance": round(float(imp), 4)}
            for name, imp in paired[:top_n]
        ]
    except Exception:
        return []
    
    
# ==================================================
# 3. PROPHET (Série Temporelle)
# ==================================================

def train_prophet(df_raw: pd.DataFrame, forecast_days: int = 7) -> dict:
    """
    Facebook Prophet - adapté aux données journalières avec saisonnalité.
    Décomposition automatique tendance + saisonnalité hebdomadaire.
    """
    # Prophet attend les colonnes 'ds' et 'y'
    prophet_df = df_raw.reset_index()
    prophet_df.columns = ['ds', 'y']
    prophet_df['ds'] = pd.to_datetime(prophet_df['ds'])

    # Séparation temporelle pour évaluation
    test_days = min(14, len(prophet_df) // 5)
    train_df = prophet_df.iloc[:-test_days]
    test_df = prophet_df.iloc[-test_days:]

    model = Prophet(
        daily_seasonality=False,
        weekly_seasonality=True,
        yearly_seasonality=True if len(prophet_df) > 180 else False,
        changepoint_prior_scale=0.05,
        seasonality_mode='multiplicative'
    )
    model.fit(train_df)

    # Évaluation sur les données de test
    future_eval = model.make_future_dataframe(periods=test_days)
    forecast_eval = model.predict(future_eval)
    y_pred_eval = forecast_eval.iloc[-test_days:]['yhat'].values
    y_pred_eval = np.maximum(y_pred_eval, 0)
    metrics = calculate_metrics(test_df['y'].values, y_pred_eval)

    # Ré-entraîner sur toutes les données pour la prévision finale
    model_full = Prophet(
        daily_seasonality=False,
        weekly_seasonality=True,
        yearly_seasonality=True if len(prophet_df) > 180 else False,
        changepoint_prior_scale=0.05,
        seasonality_mode='multiplicative'
    )
    model_full.fit(prophet_df)

    # Générer les prévisions futures
    future = model_full.make_future_dataframe(periods=forecast_days)
    forecast = model_full.predict(future)
    predictions = forecast.iloc[-forecast_days:][['ds', 'yhat', 'yhat_lower', 'yhat_upper']]

    forecast_list = []
    for _, row in predictions.iterrows():
        qty = max(0, row['yhat'])
        forecast_list.append({
            "date": row['ds'].strftime('%Y-%m-%d'),
            "quantity": round(qty, 2),
            "lower": round(max(0, row['yhat_lower']), 2),
            "upper": round(max(0, row['yhat_upper']), 2)
        })

    return {
        "model_name": "Prophet",
        "version": datetime.now().strftime("%Y%m%d_%H%M%S"),
        "metrics": metrics,
        "forecast": forecast_list
    }


# ==================================================
# 4. APPROCHE D'ENSEMBLE
# ==================================================

def ensemble_predict(lr_preds, rf_preds, prophet_preds, lr_metrics, rf_metrics, prophet_metrics) -> list:
    """
    Moyenne pondérée des prédictions basée sur les performances (1/MAPE).
    Le modèle avec le MAPE le plus bas reçoit le poids le plus élevé.
    """
    # Calcul des poids inversement proportionnels au MAPE
    mapes = [
        max(lr_metrics['mape'], 0.001),
        max(rf_metrics['mape'], 0.001),
        max(prophet_metrics['mape'], 0.001)
    ]
    inv_mapes = [1.0 / m for m in mapes]
    total = sum(inv_mapes)
    weights = [w / total for w in inv_mapes]

    ensemble = []
    for i in range(len(lr_preds)):
        weighted_qty = (
            weights[0] * lr_preds[i] +
            weights[1] * rf_preds[i] +
            weights[2] * prophet_preds[i]
        )
        ensemble.append(round(max(0, weighted_qty), 2))

    return ensemble, weights

def ensemble_predict_4(lr_preds, rf_preds, xgb_preds, prophet_preds,
                       lr_metrics, rf_metrics, xgb_metrics, prophet_metrics) -> list:
    """
    Moyenne pondérée de 4 modèles basée sur les performances (1/MAPE).
    """
    mapes = [
        max(lr_metrics['mape'], 0.001),
        max(rf_metrics['mape'], 0.001),
        max(xgb_metrics['mape'], 0.001),
        max(prophet_metrics['mape'], 0.001)
    ]
    inv_mapes = [1.0 / m for m in mapes]
    total = sum(inv_mapes)
    weights = [w / total for w in inv_mapes]

    ensemble = []
    for i in range(len(lr_preds)):
        weighted_qty = (
            weights[0] * lr_preds[i] +
            weights[1] * rf_preds[i] +
            weights[2] * xgb_preds[i] +
            weights[3] * prophet_preds[i]
        )
        ensemble.append(round(max(0, weighted_qty), 2))

    return ensemble, weights


# ==================================================
# 5. ISOLATION FOREST (Détection d'Anomalies)
# ==================================================

def detect_anomalies(df: pd.DataFrame) -> dict:
    """
    Détection d'anomalies via Isolation Forest.
    Remplace le seuil ±2σ par une approche multivariée.
    Score normalisé → classification LOW / MEDIUM / HIGH.
    """
    feature_cols = [c for c in df.columns if c != 'quantity']
    all_cols = ['quantity'] + feature_cols
    available_cols = [c for c in all_cols if c in df.columns]
    data = df[available_cols].copy()

    if len(data) < 10:
        return {"anomaly_score": 0.0, "risk_level": "LOW", "anomalies": []}

    iso_forest = IsolationForest(
        contamination=ML_CONFIG['anomaly_contamination'],
        random_state=42,
        n_estimators=100
    )

    predictions = iso_forest.fit_predict(data)
    scores = iso_forest.decision_function(data)

    # Score normalisé entre 0 et 1 (plus élevé = plus anormal)
    normalized_scores = 1 - (scores - scores.min()) / (scores.max() - scores.min() + 1e-10)

    # Dernier score (état actuel)
    latest_score = float(normalized_scores[-1])

    # Classification du niveau de risque
    if latest_score > 0.8:
        risk_level = "HIGH"
    elif latest_score > 0.5:
        risk_level = "MEDIUM"
    else:
        risk_level = "LOW"

    # Identifier les anomalies récentes (derniers 14 jours)
    recent_mask = predictions[-14:] == -1
    anomaly_dates = df.index[-14:][recent_mask].strftime('%Y-%m-%d').tolist()

    return {
        "anomaly_score": round(latest_score, 4),
        "risk_level": risk_level,
        "anomalies": anomaly_dates,
        "total_anomalies_detected": int((predictions == -1).sum()),
        "anomaly_ratio": round((predictions == -1).mean(), 4)
    }


# ==================================================
# 6. CALCUL JOURS AVANT RUPTURE
# ==================================================

def calculate_days_before_rupture(current_stock: float, predicted_daily_demand: list) -> dict:
    """
    Jours restants = Stock courant / Demande moyenne prédite.
    Si inférieur au seuil critique → alerte prioritaire.
    """
    if not predicted_daily_demand or all(d <= 0 for d in predicted_daily_demand):
        return {"days_before_rupture": None, "estimated_rupture_date": None}

    avg_demand = np.mean([d for d in predicted_daily_demand if d > 0])
    if avg_demand <= 0:
        return {"days_before_rupture": None, "estimated_rupture_date": None}

    days = current_stock / avg_demand

    # Simulation jour par jour
    stock = current_stock
    rupture_day = None
    for i, demand in enumerate(predicted_daily_demand):
        stock -= max(0, demand)
        if stock <= 0:
            rupture_day = i + 1
            break

    return {
        "days_before_rupture": round(days, 1),
        "simulated_rupture_day": rupture_day,
        "avg_predicted_demand": round(avg_demand, 2)
    }
    
    
# ==================================================
# 7. GESTION DES ANCIENS MODÈLES
# ==================================================

def cleanup_old_models(station_id: int, fuel_type_id: int, keep: int = 3):
    """Delete old model files, keeping only the most recent `keep` versions."""
    import glob
    for prefix in ["lr", "rf", "xgb", "xgbq"]:
        pattern = os.path.join(MODEL_DIR, f"{prefix}_{station_id}_{fuel_type_id}_*.pkl")
        files = sorted(glob.glob(pattern))
        for old_file in files[:-keep]:
            try:
                os.remove(old_file)
            except OSError:
                pass

# ==================================================
# 8. SHAP VALUES (per-prediction explanations)
# ==================================================

def compute_shap_values(model, X_train, X_explain, feature_names: list,
                         max_background: int = 100) -> dict | None:
    """
    Compute SHAP values for each row in X_explain using TreeExplainer.
    Works with XGBoost, RandomForest, and other tree models.
    
    Returns a dict with:
    - base_value: float (the model's expected value)
    - per_row_contributions: list[dict[feature -> float]]
    - top_features_per_row: list[list[{feature, value, shap_value}]]
    - global_top_features: list[{feature, mean_abs_shap}]
    
    Returns None if SHAP is unavailable or the model type isn't supported.
    """
    try:
        import shap
    except ImportError:
        return None

    try:
        # Use a background sample to speed up (not needed for pure tree explainer
        # but some shap versions warn without it)
        bg = X_train.sample(min(max_background, len(X_train)), random_state=42) if len(X_train) > max_background else X_train

        explainer = shap.TreeExplainer(model, bg, feature_perturbation="interventional")
        shap_values = explainer.shap_values(X_explain, check_additivity=False)

        # base_value can be an array for multi-output models; take scalar for regression
        base = explainer.expected_value
        if hasattr(base, "__len__"):
            base = float(np.asarray(base).flatten()[0])
        else:
            base = float(base)

        # Per-row: dict of feature -> shap_value
        per_row = []
        top_per_row = []
        for i in range(len(X_explain)):
            row_contribs = {fname: float(shap_values[i][j]) for j, fname in enumerate(feature_names)}
            per_row.append(row_contribs)

            # Top 5 features by absolute contribution for this row
            row_feats = [
                {
                    "feature": fname,
                    "value": float(X_explain.iloc[i][fname]) if fname in X_explain.columns else None,
                    "shap_value": float(shap_values[i][j]),
                }
                for j, fname in enumerate(feature_names)
            ]
            row_feats.sort(key=lambda r: abs(r["shap_value"]), reverse=True)
            top_per_row.append(row_feats[:5])

        # Global importance (mean absolute SHAP across rows)
        mean_abs = np.mean(np.abs(shap_values), axis=0)
        global_top = [
            {"feature": fname, "mean_abs_shap": float(mean_abs[j])}
            for j, fname in enumerate(feature_names)
        ]
        global_top.sort(key=lambda r: r["mean_abs_shap"], reverse=True)

        return {
            "base_value": base,
            "per_row_contributions": per_row,
            "top_features_per_row": top_per_row,
            "global_top_features": global_top[:10],
        }

    except Exception as e:
        import logging
        logging.getLogger(__name__).error(f"[SHAP] Error computing values: {e}")
        return None
    
    
# ==================================================
# 9. BIAS CORRECTION
# ==================================================

def correct_bias(y_pred: np.ndarray, y_train: np.ndarray, 
                 y_train_pred: np.ndarray, alpha: float = 0.5) -> np.ndarray:
    """
    Correction de biais post-prédiction.
    Calcule le biais moyen récent (sur les 14 derniers jours d'entraînement)
    et ajuste les prédictions en conséquence.
    
    alpha: facteur de lissage (0 = pas de correction, 1 = correction complète)
    """
    # Biais sur les 14 derniers jours d'entraînement
    recent_n = min(14, len(y_train))
    recent_actual = y_train[-recent_n:]
    recent_predicted = y_train_pred[-recent_n:]
    
    bias = np.mean(recent_actual - recent_predicted)
    
    corrected = y_pred + alpha * bias
    return np.maximum(corrected, 0)