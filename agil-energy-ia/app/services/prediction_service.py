import logging
import numpy as np
from datetime import datetime

from app.config.settings import ML_CONFIG
from app.services.database import (
    fetch_sales_data, fetch_tank_info, save_predictions,
    save_alert, save_model_metrics, fetch_region_sales_data
)
from app.services.feature_engineering import (
    create_features, prepare_train_test, prepare_future_features
)
from app.models.ml_models import (
    train_linear_regression, train_random_forest, train_prophet,
    ensemble_predict_4, detect_anomalies, calculate_days_before_rupture,
    train_xgboost, get_feature_importance,
    train_xgboost_quantiles, predict_quantiles, compute_shap_values,
)
from app.models.ml_models import (
    train_linear_regression, train_random_forest, train_prophet,
    ensemble_predict_4, detect_anomalies, calculate_days_before_rupture,
    train_xgboost, get_feature_importance,
    train_xgboost_quantiles, predict_quantiles, compute_shap_values,
    correct_bias,  # ← ADD THIS
)
logger = logging.getLogger(__name__)


def run_prediction_pipeline(station_id: int, fuel_type_id: int, forecast_days: int = 7,include_quantiles: bool = True,include_shap: bool = True,) -> dict:
    """
    Pipeline complet :
    1. Extraction des données depuis MySQL
    2. Feature engineering
    3. Entraînement des modèles (LR, RF, Prophet)
    4. Évaluation via métriques (MAE, RMSE, MAPE)
    5. Prévision par ensemble pondéré
    6. Détection d'anomalies (Isolation Forest)
    7. Calcul jours avant rupture
    8. Sauvegarde des résultats
    """
    result = {
        "station_id": station_id,
        "fuel_type_id": fuel_type_id,
        "forecast_days": forecast_days,
        "status": "success",
        "models": {},
        "forecast_7_days": [],
        "anomaly": {},
        "rupture": {},
        "best_model": None,
        "ensemble_weights": None,
        "quantiles": None,
        "shap": None,
    }

    # ===== 1. Extraction des données =====
    logger.info(f"[Pipeline] Station {station_id}, Fuel {fuel_type_id} - Extraction des données")
    df_raw = fetch_sales_data(station_id, fuel_type_id)

    if df_raw.empty or len(df_raw) < 30:
        logger.warning(f"Données insuffisantes: {len(df_raw)} jours (minimum 30 requis)")
        result["status"] = "insufficient_data"
        result["message"] = f"Données insuffisantes: {len(df_raw)} jours disponibles (minimum 30 requis)"

        # Méthode simplifiée : moyenne mobile
        if not df_raw.empty and len(df_raw) >= 7:
            avg = float(df_raw['quantity'].tail(7).mean())
            result["forecast_7_days"] = [round(avg, 2)] * forecast_days
            result["message"] += ". Méthode simplifiée (moyenne mobile 7 jours) utilisée."
        return result

    # ===== 2. Feature engineering =====
    logger.info(f"[Pipeline] Feature engineering sur {len(df_raw)} jours de données")
    df_features = create_features(df_raw, station_id=station_id, fuel_type_id=fuel_type_id)

    if len(df_features) < ML_CONFIG['min_data_points']:
        # Utiliser les données disponibles même si < 90 jours
        logger.warning(f"Volume de données limité: {len(df_features)} (idéal: {ML_CONFIG['min_data_points']}+)")

    # ===== 3. Séparation train/test =====
    test_days = min(14, len(df_features) // 5)
    X_train, y_train, X_test, y_test = prepare_train_test(df_features, test_days)

    if len(X_train) < 20:
        result["status"] = "insufficient_features"
        avg = float(df_raw['quantity'].tail(7).mean())
        result["forecast_7_days"] = [round(avg, 2)] * forecast_days
        result["message"] = "Données insuffisantes pour les modèles ML. Moyenne mobile utilisée."
        return result

    # ===== 4. Entraînement des modèles =====
    models_results = {}

    # 4.1 Régression Linéaire (Baseline)
    try:
        logger.info("[Pipeline] Entraînement Régression Linéaire (baseline)")
        lr_result = train_linear_regression(X_train, y_train, X_test, y_test, station_id, fuel_type_id)
        models_results["LinearRegression"] = lr_result
        save_model_metrics("LinearRegression", lr_result['version'], station_id, fuel_type_id,
                           lr_result['metrics']['mae'], lr_result['metrics']['rmse'], lr_result['metrics']['mape'])
    except Exception as e:
        logger.error(f"Erreur LR: {e}")
        models_results["LinearRegression"] = {"metrics": {"mae": 999, "rmse": 999, "mape": 999}}

    # 4.2 Random Forest
    try:
        logger.info("[Pipeline] Entraînement Random Forest")
        rf_result = train_random_forest(X_train, y_train, X_test, y_test, station_id, fuel_type_id)
        models_results["RandomForest"] = rf_result
        save_model_metrics("RandomForest", rf_result['version'], station_id, fuel_type_id,
                           rf_result['metrics']['mae'], rf_result['metrics']['rmse'], rf_result['metrics']['mape'])
    except Exception as e:
        logger.error(f"Erreur RF: {e}")
        models_results["RandomForest"] = {"metrics": {"mae": 999, "rmse": 999, "mape": 999}}
    # 4.3 XGBoost
    try:
        logger.info("[Pipeline] Entraînement XGBoost")
        xgb_result = train_xgboost(X_train, y_train, X_test, y_test, station_id, fuel_type_id)
        models_results["XGBoost"] = xgb_result
        save_model_metrics("XGBoost", xgb_result['version'], station_id, fuel_type_id,
                           xgb_result['metrics']['mae'], xgb_result['metrics']['rmse'], xgb_result['metrics']['mape'])
    except Exception as e:
        logger.error(f"Erreur XGBoost: {e}")
        models_results["XGBoost"] = {"metrics": {"mae": 999, "rmse": 999, "mape": 999}}
    # 4.3b XGBoost Quantile (prediction intervals: P10 / P50 / P90)
    quantile_result = None
    if include_quantiles:
        try:
            logger.info("[Pipeline] Entraînement XGBoost Quantile (P10/P50/P90)")
            quantile_result = train_xgboost_quantiles(
                X_train, y_train, X_test, y_test, station_id, fuel_type_id
            )
        except Exception as e:
            logger.error(f"Erreur XGBoost Quantile: {e}")
            quantile_result = None
    # 4.4 Prophet
    prophet_result = None
    """try:
        logger.info("[Pipeline] Entraînement Prophet")
        prophet_result = train_prophet(df_raw, forecast_days)
        models_results["Prophet"] = prophet_result
        save_model_metrics("Prophet", prophet_result['version'], station_id, fuel_type_id,
                           prophet_result['metrics']['mae'], prophet_result['metrics']['rmse'],
                           prophet_result['metrics']['mape'])
    except Exception as e:
        logger.error(f"Erreur Prophet: {e}")
        models_results["Prophet"] = {"metrics": {"mae": 999, "rmse": 999, "mape": 999}}"""
            # 4.4 Prophet — disabled: Stan backend unavailable in this image, and the
            # prebuilt wheel conflicts with the numpy version SHAP requires.
            # Ensemble weight was 0 regardless.

    # ===== 5. Sélection du meilleur modèle et prévisions =====
    result["models"] = {name: res.get("metrics", {}) for name, res in models_results.items()}

    # Déterminer le meilleur modèle par MAPE
    best_model = min(models_results.items(), key=lambda x: x[1].get("metrics", {}).get("mape", 999))
    result["best_model"] = best_model[0]

    # Prévisions futures avec les modèles ML (LR et RF)
    future_features = prepare_future_features(df_features, forecast_days, station_id=station_id, fuel_type_id=fuel_type_id)
    feature_cols = [c for c in df_features.columns if c != 'quantity']
    future_X = future_features[feature_cols] if set(feature_cols).issubset(future_features.columns) else future_features

    lr_preds = np.zeros(forecast_days)
    rf_preds = np.zeros(forecast_days)
    xgb_preds = np.zeros(forecast_days)
    prophet_preds = np.zeros(forecast_days)

    if "model" in models_results.get("LinearRegression", {}):
        lr_preds = np.maximum(models_results["LinearRegression"]["model"].predict(future_X), 0)

    if "model" in models_results.get("RandomForest", {}):
        rf_preds = np.maximum(models_results["RandomForest"]["model"].predict(future_X), 0)

    if "model" in models_results.get("XGBoost", {}):
        xgb_preds = np.maximum(models_results["XGBoost"]["model"].predict(future_X), 0)

    if prophet_result and "forecast" in prophet_result:
        prophet_preds = np.array([f['quantity'] for f in prophet_result['forecast'][:forecast_days]])
        if len(prophet_preds) < forecast_days:
            prophet_preds = np.pad(prophet_preds, (0, forecast_days - len(prophet_preds)), mode='edge')

    # Correction de biais : ajuster les prédictions en fonction
    # des erreurs récentes observées sur les données d'entraînement
    if "model" in models_results.get("LinearRegression", {}):
        lr_train_pred = models_results["LinearRegression"]["model"].predict(X_train)
        lr_preds = correct_bias(lr_preds, y_train.values, lr_train_pred)

    if "model" in models_results.get("RandomForest", {}):
        rf_train_pred = models_results["RandomForest"]["model"].predict(X_train)
        rf_preds = correct_bias(rf_preds, y_train.values, rf_train_pred)

    if "model" in models_results.get("XGBoost", {}):
        xgb_train_pred = models_results["XGBoost"]["model"].predict(X_train)
        xgb_preds = correct_bias(xgb_preds, y_train.values, xgb_train_pred)
    
    # Ensemble pondéré (4 modèles)
    lr_metrics = models_results.get("LinearRegression", {}).get("metrics", {"mape": 999})
    rf_metrics = models_results.get("RandomForest", {}).get("metrics", {"mape": 999})
    xgb_metrics = models_results.get("XGBoost", {}).get("metrics", {"mape": 999})
    pr_metrics = models_results.get("Prophet", {}).get("metrics", {"mape": 999})

    ensemble_preds, weights = ensemble_predict_4(
        lr_preds, rf_preds, xgb_preds, prophet_preds,
        lr_metrics, rf_metrics, xgb_metrics, pr_metrics
    )
    result["forecast_7_days"] = ensemble_preds
    result["ensemble_weights"] = {
        "LinearRegression": round(weights[0], 3),
        "RandomForest": round(weights[1], 3),
        "XGBoost": round(weights[2], 3),
        #"Prophet": round(weights[3], 3)
    }
    
    # ===== 5b. Quantile predictions =====
    if quantile_result and "models" in quantile_result:
        try:
            q_future = predict_quantiles(
                quantile_result["models"],
                future_X,
                calibration_offset=quantile_result.get("calibration_offset", 0.0),
            )
            p10 = [round(float(max(0, v)), 2) for v in q_future[0.1]]
            p50 = [round(float(max(0, v)), 2) for v in q_future[0.5]]
            p90 = [round(float(max(0, v)), 2) for v in q_future[0.9]]
            result["quantiles"] = {
                "P10": p10,
                "P50": p50,
                "P90": p90,
                "metrics": {k: float(v) for k, v in quantile_result["metrics"].items()},
            }
            logger.info(
                f"[Pipeline] Quantiles: coverage_raw={quantile_result['metrics'].get('coverage_80_pct')}%, "
                f"coverage_calibrated={quantile_result['metrics'].get('coverage_80_calibrated_pct')}%, "
                f"offset={quantile_result['metrics'].get('calibration_offset')}"
            )
        except Exception as e:
            logger.error(f"Erreur prévision quantiles: {e}")
            result["quantiles"] = None
    # ===== 5c. SHAP explanations (per-day feature attributions) =====
    if include_shap:
        try:
            # Pick the best tree-based model for SHAP (XGBoost preferred, then RF)
            shap_model = None
            if "model" in models_results.get("XGBoost", {}):
                shap_model = models_results["XGBoost"]["model"]
            elif "model" in models_results.get("RandomForest", {}):
                shap_model = models_results["RandomForest"]["model"]

            if shap_model is not None:
                logger.info("[Pipeline] Calcul des valeurs SHAP")
                shap_feature_names = list(X_train.columns)
                shap_result = compute_shap_values(
                    shap_model,
                    X_train,
                    future_X,
                    shap_feature_names,
                )
                if shap_result:
                    result["shap"] = {
                        "base_value": float(round(shap_result["base_value"], 2)),
                        "top_features_per_day": [
                            [
                                {
                                    "feature": str(f["feature"]),
                                    "value": float(round(f["value"], 3)) if f["value"] is not None else None,
                                    "shap_value": float(round(f["shap_value"], 2)),
                                }
                                for f in day
                            ]
                            for day in shap_result["top_features_per_row"]
                        ],
                        "global_top_features": [
                            {"feature": str(f["feature"]), "mean_abs_shap": float(round(f["mean_abs_shap"], 3))}
                            for f in shap_result["global_top_features"]
                        ],
                    }
                    logger.info("[Pipeline] SHAP calculé avec succès")
        except Exception as e:
            logger.error(f"Erreur SHAP: {e}")
            result["shap"] = None
    
    # Feature importance (from best tree-based model)
    best_tree_model = None
    feature_names = list(X_train.columns)
    if "model" in models_results.get("XGBoost", {}):
        best_tree_model = models_results["XGBoost"]["model"]
    elif "model" in models_results.get("RandomForest", {}):
        best_tree_model = models_results["RandomForest"]["model"]

    if best_tree_model:
        result["feature_importance"] = get_feature_importance(best_tree_model, feature_names)

    # ===== 6. Détection d'anomalies =====
    logger.info("[Pipeline] Détection d'anomalies (Isolation Forest)")
    anomaly_result = detect_anomalies(df_features)
    result["anomaly"] = anomaly_result

    # ===== 7. Calcul jours avant rupture =====
    tank_info = fetch_tank_info(station_id, fuel_type_id)
    if tank_info:
        # Use the P90 (pessimistic) forecast when available for safer alerting.
        # This means: if demand could be higher than expected, warn earlier.
        forecast_for_rupture = ensemble_preds
        rupture_source = "ensemble"
        if result.get("quantiles") and result["quantiles"].get("P90"):
            forecast_for_rupture = result["quantiles"]["P90"]
            rupture_source = "P90 (pessimistic)"

        rupture = calculate_days_before_rupture(
            float(tank_info['current_stock']),
            forecast_for_rupture
        )
        rupture["source"] = rupture_source
        result["rupture"] = rupture

        # Also compute a "best-case" rupture using P10 for reference
        if result.get("quantiles") and result["quantiles"].get("P10"):
            best_case = calculate_days_before_rupture(
                float(tank_info['current_stock']),
                result["quantiles"]["P10"]
            )
            result["rupture"]["days_before_rupture_optimistic"] = best_case.get("days_before_rupture")

        # Générer alerte si rupture imminente (basée sur le scénario pessimiste)
        if rupture.get("days_before_rupture") and rupture["days_before_rupture"] <= 3:
            severity = "HIGH" if rupture["days_before_rupture"] <= 1 else "MEDIUM"
            save_alert(
                station_id, "STOCK_RUPTURE", severity,
                f"Rupture estimée dans {rupture['days_before_rupture']} jours (scénario {rupture_source}). "
                f"Stock: {tank_info['current_stock']}L, Demande moy: {rupture['avg_predicted_demand']}L/j"
            )

    # Générer alerte anomalie si score élevé
    if anomaly_result.get("risk_level") in ["HIGH", "MEDIUM"]:
        save_alert(
            station_id, "ANOMALY", anomaly_result["risk_level"],
            f"Anomalie détectée (score: {anomaly_result['anomaly_score']}). "
            f"Niveau de risque: {anomaly_result['risk_level']}. "
            f"Dates suspectes: {', '.join(anomaly_result.get('anomalies', []))}"
        )

    # ===== 8. Sauvegarde des prévisions =====
    logger.info("[Pipeline] Sauvegarde des prévisions")
    from datetime import timedelta
    today = datetime.now().date()
    predictions_to_save = []
    for i, qty in enumerate(ensemble_preds):
        pred_date = today + timedelta(days=i + 1)
        best_mape = models_results[result['best_model']].get('metrics', {}).get('mape', 0.5)
        confidence = 1.0 - min(best_mape, 1.0)
        predictions_to_save.append({
            "date": pred_date.strftime('%Y-%m-%d'),
            "quantity": qty,
            "confidence": round(confidence, 4)
        })

    save_predictions(station_id, fuel_type_id, predictions_to_save, f"Ensemble({result['best_model']})")

    logger.info(f"[Pipeline] Terminé - Meilleur modèle: {result['best_model']}, "
                f"MAPE: {models_results[result['best_model']]['metrics']['mape']:.4f}")
    
    # Cleanup old model files
    from app.models.ml_models import cleanup_old_models
    cleanup_old_models(station_id, fuel_type_id)
    
    return result

def run_region_prediction_pipeline(region: str, fuel_type_id: int, forecast_days: int = 7) -> dict:
    """
    Pipeline de prédiction agrégée par région.
    Agrège les ventes de toutes les stations d'une région,
    puis applique le même pipeline ML (LR, RF, XGBoost, Prophet + Ensemble).
    """
    result = {
        "region": region,
        "fuel_type_id": fuel_type_id,
        "forecast_days": forecast_days,
        "status": "success",
        "models": {},
        "forecast_7_days": [],
        "anomaly": {},
        "best_model": None,
        "ensemble_weights": None
    }

    # ===== 1. Extraction des données agrégées par région =====
    logger.info(f"[RegionPipeline] Région '{region}', Fuel {fuel_type_id} - Extraction des données")
    df_raw = fetch_region_sales_data(region, fuel_type_id)

    if df_raw.empty or len(df_raw) < 30:
        logger.warning(f"Données insuffisantes pour la région '{region}': {len(df_raw)} jours")
        result["status"] = "insufficient_data"
        result["message"] = f"Données insuffisantes pour la région '{region}': {len(df_raw)} jours (minimum 30 requis)"

        if not df_raw.empty and len(df_raw) >= 7:
            avg = float(df_raw['quantity'].tail(7).mean())
            result["forecast_7_days"] = [round(avg, 2)] * forecast_days
            result["message"] += ". Méthode simplifiée (moyenne mobile 7 jours) utilisée."
        return result

    # ===== 2. Feature engineering (sans station_id, features contextuelles limitées) =====
    logger.info(f"[RegionPipeline] Feature engineering sur {len(df_raw)} jours de données")
    df_features = create_features(df_raw, station_id=None, fuel_type_id=fuel_type_id)

    if len(df_features) < ML_CONFIG['min_data_points']:
        logger.warning(f"Volume de données limité: {len(df_features)} (idéal: {ML_CONFIG['min_data_points']}+)")

    # ===== 3. Séparation train/test =====
    test_days = min(14, len(df_features) // 5)
    X_train, y_train, X_test, y_test = prepare_train_test(df_features, test_days)

    if len(X_train) < 20:
        result["status"] = "insufficient_features"
        avg = float(df_raw['quantity'].tail(7).mean())
        result["forecast_7_days"] = [round(avg, 2)] * forecast_days
        result["message"] = "Données insuffisantes pour les modèles ML. Moyenne mobile utilisée."
        return result

    # ===== 4. Entraînement des modèles =====
    models_results = {}

    # Use a synthetic station_id for model caching (hash of region name)
    region_hash_id = abs(hash(region)) % 100000 + 100000  # 6-digit ID to avoid collision with real stations

    # 4.1 Linear Regression
    try:
        logger.info("[RegionPipeline] Entraînement Régression Linéaire")
        lr_result = train_linear_regression(X_train, y_train, X_test, y_test, region_hash_id, fuel_type_id)
        models_results["LinearRegression"] = lr_result
    except Exception as e:
        logger.error(f"Erreur LR (région): {e}")
        models_results["LinearRegression"] = {"metrics": {"mae": 999, "rmse": 999, "mape": 999}}

    # 4.2 Random Forest
    try:
        logger.info("[RegionPipeline] Entraînement Random Forest")
        rf_result = train_random_forest(X_train, y_train, X_test, y_test, region_hash_id, fuel_type_id)
        models_results["RandomForest"] = rf_result
    except Exception as e:
        logger.error(f"Erreur RF (région): {e}")
        models_results["RandomForest"] = {"metrics": {"mae": 999, "rmse": 999, "mape": 999}}

    # 4.3 XGBoost
    try:
        logger.info("[RegionPipeline] Entraînement XGBoost")
        xgb_result = train_xgboost(X_train, y_train, X_test, y_test, region_hash_id, fuel_type_id)
        models_results["XGBoost"] = xgb_result
    except Exception as e:
        logger.error(f"Erreur XGBoost (région): {e}")
        models_results["XGBoost"] = {"metrics": {"mae": 999, "rmse": 999, "mape": 999}}

    # 4.4 Prophet
    prophet_result = None
    """try:
        logger.info("[RegionPipeline] Entraînement Prophet")
        prophet_result = train_prophet(df_raw, forecast_days)
        models_results["Prophet"] = prophet_result
    except Exception as e:
        logger.error(f"Erreur Prophet (région): {e}")
        models_results["Prophet"] = {"metrics": {"mae": 999, "rmse": 999, "mape": 999}}"""
            # 4.4 Prophet — disabled: Stan backend unavailable in this image, and the
            # prebuilt wheel conflicts with the numpy version SHAP requires.
            # Ensemble weight was 0 regardless.

    # ===== 5. Sélection du meilleur modèle et prévisions =====
    result["models"] = {name: res.get("metrics", {}) for name, res in models_results.items()}

    best_model = min(models_results.items(), key=lambda x: x[1].get("metrics", {}).get("mape", 999))
    result["best_model"] = best_model[0]

    # Prévisions futures
    future_features = prepare_future_features(df_features, forecast_days, station_id=None, fuel_type_id=fuel_type_id)
    feature_cols = [c for c in df_features.columns if c != 'quantity']
    future_X = future_features[feature_cols] if set(feature_cols).issubset(future_features.columns) else future_features

    lr_preds = np.zeros(forecast_days)
    rf_preds = np.zeros(forecast_days)
    xgb_preds = np.zeros(forecast_days)
    prophet_preds = np.zeros(forecast_days)

    if "model" in models_results.get("LinearRegression", {}):
        lr_preds = np.maximum(models_results["LinearRegression"]["model"].predict(future_X), 0)

    if "model" in models_results.get("RandomForest", {}):
        rf_preds = np.maximum(models_results["RandomForest"]["model"].predict(future_X), 0)

    if "model" in models_results.get("XGBoost", {}):
        xgb_preds = np.maximum(models_results["XGBoost"]["model"].predict(future_X), 0)

    if prophet_result and "forecast" in prophet_result:
        prophet_preds = np.array([f['quantity'] for f in prophet_result['forecast'][:forecast_days]])
        if len(prophet_preds) < forecast_days:
            prophet_preds = np.pad(prophet_preds, (0, forecast_days - len(prophet_preds)), mode='edge')

    # Correction de biais : ajuster les prédictions en fonction
    # des erreurs récentes observées sur les données d'entraînement
    if "model" in models_results.get("LinearRegression", {}):
        lr_train_pred = models_results["LinearRegression"]["model"].predict(X_train)
        lr_preds = correct_bias(lr_preds, y_train.values, lr_train_pred)

    if "model" in models_results.get("RandomForest", {}):
        rf_train_pred = models_results["RandomForest"]["model"].predict(X_train)
        rf_preds = correct_bias(rf_preds, y_train.values, rf_train_pred)

    if "model" in models_results.get("XGBoost", {}):
        xgb_train_pred = models_results["XGBoost"]["model"].predict(X_train)
        xgb_preds = correct_bias(xgb_preds, y_train.values, xgb_train_pred)
    
    # Ensemble pondéré
    lr_metrics = models_results.get("LinearRegression", {}).get("metrics", {"mape": 999})
    rf_metrics = models_results.get("RandomForest", {}).get("metrics", {"mape": 999})
    xgb_metrics = models_results.get("XGBoost", {}).get("metrics", {"mape": 999})
    pr_metrics = models_results.get("Prophet", {}).get("metrics", {"mape": 999})

    ensemble_preds, weights = ensemble_predict_4(
        lr_preds, rf_preds, xgb_preds, prophet_preds,
        lr_metrics, rf_metrics, xgb_metrics, pr_metrics
    )
    result["forecast_7_days"] = ensemble_preds
    result["ensemble_weights"] = {
        "LinearRegression": round(weights[0], 3),
        "RandomForest": round(weights[1], 3),
        "XGBoost": round(weights[2], 3),
        #"Prophet": round(weights[3], 3)
    }

    # Feature importance
    best_tree_model = None
    feature_names = list(X_train.columns)
    if "model" in models_results.get("XGBoost", {}):
        best_tree_model = models_results["XGBoost"]["model"]
    elif "model" in models_results.get("RandomForest", {}):
        best_tree_model = models_results["RandomForest"]["model"]

    if best_tree_model:
        result["feature_importance"] = get_feature_importance(best_tree_model, feature_names)

    # ===== 6. Détection d'anomalies =====
    logger.info("[RegionPipeline] Détection d'anomalies")
    anomaly_result = detect_anomalies(df_features)
    result["anomaly"] = anomaly_result

    # Cleanup old model files
    from app.models.ml_models import cleanup_old_models
    cleanup_old_models(region_hash_id, fuel_type_id)

    logger.info(f"[RegionPipeline] Terminé - Région '{region}', Meilleur modèle: {result['best_model']}")
    return result