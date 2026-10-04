"""
AGIL Energy — Évaluation (Conditions Normales)
================================================
Identique à model_evaluation.py mais filtre automatiquement
les jours de pics anormaux dans le jeu de test.

Le seuil est calculé à partir des données d'entraînement :
  seuil = mean(y_train) + 3 * std(y_train)

Tout jour de test où y_réel > seuil est exclu de l'évaluation.

Usage :
    python -m app.evaluation.evaluate_normal --station=1 --fuel=1 --output=evaluation_normal
"""

import os
import sys
import numpy as np
import pandas as pd

from sklearn.linear_model import LinearRegression
from sklearn.ensemble import RandomForestRegressor

from app.evaluation.model_evaluation import (
    compute_extended_metrics,
    train_xgboost_with_history,
    plot_loss_curves,
    plot_accuracy_curves,
    plot_residual_analysis,
    plot_actual_vs_predicted,
    plot_model_comparison,
    plot_error_by_period,
    plot_cumulative_error,
    plot_forecast_bias,
    plot_cross_validation,
    plot_learning_curves,
    plot_feature_importance_comparison,
    save_metrics_table,
    plot_performance_evolution,
)


def run_normal_evaluation(station_id: int = 1, fuel_type_id: int = 1,
                           output_dir: str = "evaluation_normal") -> dict:
    os.makedirs(output_dir, exist_ok=True)
    files = []
    all_metrics = []

    # ── 1. Charger les données depuis la BD ──
    print(f"\n{'='*60}")
    print(f"  ÉVALUATION — CONDITIONS NORMALES (sans pics)")
    print(f"  Station: {station_id} | Carburant: {fuel_type_id}")
    print(f"{'='*60}\n")

    from app.services.database import fetch_sales_data
    from app.services.feature_engineering import create_features, prepare_train_test

    df_raw = fetch_sales_data(station_id, fuel_type_id)
    if df_raw.empty or len(df_raw) < 30:
        print(f"   Données insuffisantes ({len(df_raw)} jours)")
        return {"metrics": [], "files": [], "output_dir": output_dir}

    df_features = create_features(df_raw, station_id=station_id, fuel_type_id=fuel_type_id)
    test_days = min(14, len(df_features) // 5)
    X_train, y_train, X_test, y_test = prepare_train_test(df_features, test_days)

    # ── 2. Calculer le seuil et filtrer ──
    y_train_arr = np.array(y_train)
    threshold = y_train_arr.mean() + 3 * y_train_arr.std()

    # Also use IQR as a secondary check
    q75 = np.percentile(y_train_arr, 75)
    q25 = np.percentile(y_train_arr, 25)
    iqr = q75 - q25
    threshold_iqr = q75 + 2.0 * iqr

    # Use the more conservative (lower) threshold
    final_threshold = min(threshold, threshold_iqr)

    mask_normal = np.array(y_test) <= final_threshold
    n_removed = (~mask_normal).sum()
    n_kept = mask_normal.sum()

    print(f"[Filtrage]")
    print(f"   Moyenne entraînement : {y_train_arr.mean():.0f} L")
    print(f"   Écart-type          : {y_train_arr.std():.0f} L")
    print(f"   Seuil (mean+3σ)     : {threshold:.0f} L")
    print(f"   Seuil (IQR)         : {threshold_iqr:.0f} L")
    print(f"   Seuil retenu        : {final_threshold:.0f} L")
    print(f"   Jours test total    : {len(y_test)}")
    print(f"   Jours EXCLUS (pics) : {n_removed}  ← ces jours-là étaient des tests")
    print(f"   Jours GARDÉS        : {n_kept}")

    if n_kept < 5:
        print("   ⚠ Moins de 5 jours restants, évaluation impossible.")
        return {"metrics": [], "files": [], "output_dir": output_dir}

    # Show which days were removed
    removed_dates = X_test.index[~mask_normal]
    removed_values = np.array(y_test)[~mask_normal]
    print(f"\n   Jours exclus :")
    for d, v in zip(removed_dates, removed_values):
        print(f"     {d.strftime('%Y-%m-%d')} → {v:.0f} L (au-dessus de {final_threshold:.0f})")

    # Apply the filter
    X_test_clean = X_test[mask_normal]
    y_test_clean = y_test[mask_normal]
    test_dates = X_test_clean.index

    print(f"\n   Plage test nettoyée : {test_dates[0].strftime('%Y-%m-%d')} → {test_dates[-1].strftime('%Y-%m-%d')}")

    # ── 3. Entraînement (sur les mêmes données train, inchangées) ──
    print("\n[Modèles] Entraînement sur données train complètes...")

    lr = LinearRegression().fit(X_train, y_train)
    lr_pred = np.maximum(lr.predict(X_test_clean), 0)

    rf = RandomForestRegressor(n_estimators=100, max_depth=10, random_state=42, n_jobs=-1)
    rf.fit(X_train, y_train)
    rf_pred = np.maximum(rf.predict(X_test_clean), 0)

    xgb, xgb_eval = train_xgboost_with_history(X_train, y_train, X_test_clean, y_test_clean)
    xgb_pred = np.maximum(xgb.predict(X_test_clean), 0)

    preds = {
        'LinearRegression': lr_pred,
        'RandomForest': rf_pred,
        'XGBoost': xgb_pred,
    }
    print("   ✓ LR, RF, XGBoost entraînés")

    # ── 4. Métriques ──
    print("\n[Métriques] Conditions normales uniquement :")
    for name, pred in preds.items():
        m = compute_extended_metrics(y_test_clean, pred, name, y_train_arr)
        all_metrics.append(m)
        print(f"   {name}: MAE={m['MAE']} | R²={m['R2']} | MASE={m['MASE']} | "
              f"Biais={m['Forecast_Bias_Pct']}% | DirAcc={m['Directional_Accuracy']}")

    # ── 5. Graphiques ──
    print("\n[Graphiques]")
    feature_cols = list(X_train.columns)

    csv_path, img = save_metrics_table(all_metrics, output_dir)
    files.extend([csv_path, img])
    print("   ✓ Tableau récapitulatif")

    files.append(plot_loss_curves(xgb_eval, output_dir))
    print("   ✓ Courbes de perte")

    files.append(plot_accuracy_curves(xgb_eval, float(y_test_clean.mean()), output_dir))
    print("   ✓ Courbe de précision")

    for name, pred in preds.items():
        files.append(plot_residual_analysis(y_test_clean, pred, name, output_dir))
    print(f"   ✓ Analyse des résidus (×{len(preds)})")

    files.append(plot_actual_vs_predicted(y_test_clean, preds, output_dir, dates=test_dates))
    print("   ✓ Actual vs Predicted")

    files.append(plot_model_comparison(all_metrics, output_dir))
    print("   ✓ Comparaison des modèles")

    files.append(plot_error_by_period(y_test_clean, preds, test_dates, output_dir))
    print("   ✓ Erreur par jour/mois")

    files.append(plot_cumulative_error(y_test_clean, preds, output_dir, dates=test_dates))
    print("   ✓ Erreur cumulative")

    files.append(plot_forecast_bias(y_test_clean, preds, output_dir))
    print("   ✓ Biais de prévision")

    X_all = pd.concat([X_train, X_test_clean])
    y_all = pd.concat([y_train, y_test_clean])

    cv_path, _ = plot_cross_validation(X_all, y_all, output_dir)
    files.append(cv_path)
    print("   ✓ Cross-validation")

    files.append(plot_learning_curves(X_all, y_all, output_dir))
    print("   ✓ Courbes d'apprentissage")

    files.append(plot_feature_importance_comparison(
        {'RandomForest': rf, 'XGBoost': xgb}, feature_cols, output_dir))
    print("   ✓ Feature importance")

    # Performance evolution
    try:
        from app.services.database import fetch_model_metrics
        history = fetch_model_metrics(station_id, fuel_type_id, limit=100)
        if history:
            f = plot_performance_evolution(history, output_dir)
            if f:
                files.append(f)
                print("   ✓ Évolution des performances")
    except Exception:
        pass

    print(f"\n{'='*60}")
    print(f"   {len(files)} fichiers → {output_dir}/")
    print(f"  {n_removed} jours de pics exclus, {n_kept} jours évalués")
    print(f"{'='*60}\n")

    return {"metrics": all_metrics, "files": files, "output_dir": output_dir}


if __name__ == "__main__":
    station_id, fuel_type_id = 1, 1
    output_dir = "evaluation_normal"

    for arg in sys.argv[1:]:
        if arg.startswith("--station="):
            station_id = int(arg.split("=")[1])
        elif arg.startswith("--fuel="):
            fuel_type_id = int(arg.split("=")[1])
        elif arg.startswith("--output="):
            output_dir = arg.split("=")[1]

    run_normal_evaluation(station_id, fuel_type_id, output_dir)