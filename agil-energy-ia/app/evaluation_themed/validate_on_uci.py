"""
AGIL Energy — Validation sur Dataset Public (UCI Appliances Energy)
====================================================================
[THÈME AGIL — package autonome]

CE QUE CE SCRIPT FAIT :
────────────────────────
1. Télécharge le dataset UCI "Appliances Energy Prediction"
   (19 735 observations, 10-min intervals, consommation énergétique)
2. Le transforme au même format que votre pipeline attend :
   - Resample de 10 min → journalier (somme)
   - Colonne cible → "quantity"
   - Ajoute les mêmes features temporelles (lags, rolling, cyclique)
3. Entraîne les mêmes modèles (LR, RF, XGBoost)
4. Génère les mêmes graphiques d'évaluation (thème AGIL)
5. Produit un tableau comparatif : "nos modèles sur nos données" vs "nos modèles sur UCI"

⚡ Version re-thématisée : importe les fonctions de tracé depuis le package
   themed `app.evaluation_themed.model_evaluation`. Aucun fichier d'origine
   n'est modifié.

USAGE :
───────
  cd agil-energy-ia
  pip install ucimlrepo
  python -m app.evaluation_themed.validate_on_uci --output=evaluation_uci_themed
"""

import os
import sys
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker

from sklearn.linear_model import LinearRegression
from sklearn.ensemble import RandomForestRegressor
from xgboost import XGBRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

# ⚡ Import depuis le package THEMED (le thème AGIL est appliqué à l'import)
from app.evaluation_themed.model_evaluation import (
    compute_extended_metrics,
    train_xgboost_with_history,
    plot_loss_curves,
    plot_accuracy_curves,
    plot_residual_analysis,
    plot_actual_vs_predicted,
    plot_model_comparison,
    plot_cumulative_error,
    plot_forecast_bias,
    plot_cross_validation,
    plot_learning_curves,
    plot_feature_importance_comparison,
    save_metrics_table,
    COLORS,
)
from app.evaluation_themed.agil_theme import AGIL   # couleurs de la charte AGIL


# =====================================================
# ÉTAPE 1 : CHARGER LE DATASET UCI
# =====================================================

def load_uci_dataset() -> pd.DataFrame:
    """
    Télécharge le dataset UCI Appliances Energy Prediction.
    Installe ucimlrepo si nécessaire.
    """
    try:
        from ucimlrepo import fetch_ucirepo
    except ImportError:
        print("Installation de ucimlrepo...")
        import subprocess
        subprocess.check_call([sys.executable, "-m", "pip", "install", "ucimlrepo", "--quiet"])
        from ucimlrepo import fetch_ucirepo

    print("[UCI] Téléchargement du dataset Appliances Energy Prediction...")
    dataset = fetch_ucirepo(id=374)

    # Combiner features et target
    X = dataset.data.features
    y = dataset.data.targets

    df = X.copy()
    df['Appliances'] = y['Appliances'].values
    # Fix malformed date strings like "2016-01-1117:00:00" (missing space between date and time)
    df['date'] = df['date'].astype(str).str.replace(
        r'(\d{4}-\d{2}-\d{2})(\d{2}:\d{2}:\d{2})', r'\1 \2', regex=True
    )
    df['date'] = pd.to_datetime(df['date'], format='%Y-%m-%d %H:%M:%S')
    df = df.set_index('date').sort_index()

    print(f"[UCI] Dataset chargé : {len(df)} observations, {df.shape[1]} colonnes")
    print(f"[UCI] Période : {df.index[0]} → {df.index[-1]}")
    print(f"[UCI] Fréquence : 10 minutes")

    return df


# =====================================================
# ÉTAPE 2 : TRANSFORMER AU FORMAT DE NOTRE PIPELINE
#           (uniquement features externes — pas de fuite de données)
# =====================================================

def transform_to_daily(df: pd.DataFrame) -> pd.DataFrame:
    """
    Transforme le dataset UCI (10 min) en données journalières.

    N'utilise QUE des features externes/météo (pas de capteurs intérieurs) :
    les capteurs intérieurs (T1-T9, RH_1-RH_9) sont des CONSÉQUENCES de la
    consommation, pas des causes — les inclure créerait une fuite de données
    où LR pourrait reconstruire la consommation à partir de ses propres effets.
    """
    daily = pd.DataFrame()

    # Cible : consommation totale journalière
    daily['quantity'] = df['Appliances'].resample('D').sum()

    # ── Uniquement features externes / météo ──
    # Température extérieure
    for col in ['T_out', 'To']:
        if col in df.columns:
            daily['temperature_moy'] = df[col].resample('D').mean()
            break

    # Humidité extérieure
    for col in ['RH_out', 'RH_6']:
        if col in df.columns:
            daily['humidite_pct'] = df[col].resample('D').mean()
            break

    # Pression atmosphérique
    if 'Press_mm_hg' in df.columns:
        daily['pression'] = df['Press_mm_hg'].resample('D').mean()

    # Vitesse du vent
    if 'Windspeed' in df.columns:
        daily['vent_kmh'] = df['Windspeed'].resample('D').mean()

    # Visibilité
    if 'Visibility' in df.columns:
        daily['visibilite'] = df['Visibility'].resample('D').mean()

    # Point de rosée
    if 'Tdewpoint' in df.columns:
        daily['point_rosee'] = df['Tdewpoint'].resample('D').mean()

    daily = daily.dropna()
    print(f"[UCI] Données journalières : {len(daily)} jours, {daily.shape[1]} colonnes")
    print(f"[UCI] Features retenues : {[c for c in daily.columns if c != 'quantity']}")

    return daily


# =====================================================
# ÉTAPE 3 : FEATURE ENGINEERING (identique à notre pipeline)
# =====================================================

def create_features_uci(df: pd.DataFrame) -> pd.DataFrame:

    data = df.copy()

    # ── Variables temporelles (connues d'avance — aucune fuite) ──
    data['day_of_week'] = data.index.dayofweek
    data['month'] = data.index.month
    data['quarter'] = data.index.quarter
    data['is_weekend'] = (data.index.dayofweek >= 5).astype(int)
    data['day_of_month'] = data.index.day

    data['day_sin'] = np.sin(2 * np.pi * data['day_of_week'] / 7)
    data['day_cos'] = np.cos(2 * np.pi * data['day_of_week'] / 7)
    data['month_sin'] = np.sin(2 * np.pi * data['month'] / 12)
    data['month_cos'] = np.cos(2 * np.pi * data['month'] / 12)

    # ── Lag features (strictement passées — OK) ──
    data['lag_1'] = data['quantity'].shift(1)
    data['lag_7'] = data['quantity'].shift(7)
    data['lag_14'] = data['quantity'].shift(14)

    # ── Rolling statistics (CALCULÉES SUR q_past = quantity décalé de 1) ──
    q_past = data['quantity'].shift(1)
    data['rolling_mean_7'] = q_past.rolling(7).mean()
    data['rolling_mean_14'] = q_past.rolling(14).mean()
    data['rolling_std_7'] = q_past.rolling(7).std()
    data['rolling_std_14'] = q_past.rolling(14).std()

    # ===== daily_change / daily_diff : SUPPRIMÉES (fuite de cible exacte) =====

    data['ratio_7_14'] = data['rolling_mean_7'] / data['rolling_mean_14'].replace(0, np.nan)

    # Pente de tendance sur 7 jours, calculée sur q_past (fenêtre [i-7..i-1])
    def rolling_slope(series, window=7):
        slopes = [np.nan] * (window - 1)
        x = np.arange(window)
        vals = series.values
        for i in range(window - 1, len(series)):
            y = vals[i - window + 1:i + 1]
            if len(y) == window and not np.any(np.isnan(y)):
                slopes.append(np.polyfit(x, y, 1)[0])
            else:
                slopes.append(np.nan)
        return pd.Series(slopes, index=series.index)

    data['trend_slope_7'] = rolling_slope(q_past, 7)

    # EWM décalé de 1 (ne voit pas quantity[i])
    data['ewm_7'] = q_past.ewm(span=7).mean()

    # Les colonnes météo (temperature_moy, humidite_pct, etc.) sont déjà dans df

    data = data.dropna()
    return data


# =====================================================
# ÉTAPE 4 : EXÉCUTER L'ÉVALUATION
# =====================================================

def run_uci_evaluation(output_dir: str = "evaluation_uci_themed") -> dict:
    """
    Pipeline complet : chargement → transformation → évaluation.
    """
    os.makedirs(output_dir, exist_ok=True)
    files = []
    all_metrics = []

    print(f"\n{'='*60}")
    print(f"  VALIDATION SUR DATASET PUBLIC (UCI) [THÈME AGIL]")
    print(f"  Appliances Energy Prediction — Candanedo et al., 2017")
    print(f"{'='*60}\n")

    # ── 1. Charger et transformer ──
    df_raw = load_uci_dataset()
    df_daily = transform_to_daily(df_raw)
    df_features = create_features_uci(df_daily)

    # ── 2. Split train/test ──
    test_days = min(14, len(df_features) // 5)
    feature_cols = [c for c in df_features.columns if c != 'quantity']

    X_train = df_features.iloc[:-test_days][feature_cols]
    y_train = df_features.iloc[:-test_days]['quantity']
    X_test = df_features.iloc[-test_days:][feature_cols]
    y_test = df_features.iloc[-test_days:]['quantity']
    test_dates = X_test.index

    print(f"\n[Split] {len(X_train)} train / {len(X_test)} test / {len(feature_cols)} features")

    # ── 3. Entraînement ──
    print("\n[Modèles] Entraînement...")

    lr = LinearRegression().fit(X_train, y_train)
    lr_pred = np.maximum(lr.predict(X_test), 0)

    rf = RandomForestRegressor(n_estimators=100, max_depth=10, random_state=42, n_jobs=-1)
    rf.fit(X_train, y_train)
    rf_pred = np.maximum(rf.predict(X_test), 0)

    xgb, xgb_eval = train_xgboost_with_history(X_train, y_train, X_test, y_test)
    xgb_pred = np.maximum(xgb.predict(X_test), 0)

    preds = {
        'LinearRegression': lr_pred,
        'RandomForest': rf_pred,
        'XGBoost': xgb_pred,
    }

    # ── 4. Métriques ──
    print("\n[Métriques]")
    y_train_arr = np.array(y_train)
    for name, pred in preds.items():
        m = compute_extended_metrics(y_test, pred, name, y_train_arr)
        all_metrics.append(m)
        print(f"   {name}: MAE={m['MAE']} | R²={m['R2']} | MASE={m['MASE']} | "
              f"Biais={m['Forecast_Bias_Pct']}% | DirAcc={m['Directional_Accuracy']}")

    # ── 5. Graphiques (mêmes fonctions que l'évaluation principale, thème AGIL) ──
    print("\n[Graphiques]")

    csv_path, img = save_metrics_table(all_metrics, output_dir)
    files.extend([csv_path, img])
    print("   ✓ Tableau récapitulatif")

    files.append(plot_loss_curves(xgb_eval, output_dir))
    print("   ✓ Courbes de perte")

    files.append(plot_accuracy_curves(xgb_eval, float(y_test.mean()), output_dir))
    print("   ✓ Courbe de précision")

    for name, pred in preds.items():
        files.append(plot_residual_analysis(y_test, pred, name, output_dir))
    print("   ✓ Analyse des résidus")

    files.append(plot_actual_vs_predicted(y_test, preds, output_dir, dates=test_dates))
    print("   ✓ Actual vs Predicted")

    files.append(plot_model_comparison(all_metrics, output_dir))
    print("   ✓ Comparaison des modèles")

    files.append(plot_cumulative_error(y_test, preds, output_dir, dates=test_dates))
    print("   ✓ Erreur cumulative")

    files.append(plot_forecast_bias(y_test, preds, output_dir))
    print("   ✓ Biais de prévision")

    X_all = pd.concat([X_train, X_test])
    y_all = pd.concat([y_train, y_test])

    cv_path, _ = plot_cross_validation(X_all, y_all, output_dir)
    files.append(cv_path)
    print("   ✓ Cross-validation")

    files.append(plot_learning_curves(X_all, y_all, output_dir))
    print("   ✓ Courbes d'apprentissage")

    files.append(plot_feature_importance_comparison(
        {'RandomForest': rf, 'XGBoost': xgb}, feature_cols, output_dir))
    print("   ✓ Feature importance")

    # ── 6. Graphique BONUS : Dataset UCI — visualisation de la série (thème AGIL) ──
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 8))
    ax1.plot(df_daily.index, df_daily['quantity'], color=AGIL['yellow'], lw=1.5)
    ax1.set_ylabel('Consommation (Wh/jour)')
    ax1.set_title('Dataset UCI — Consommation Énergétique Journalière',
                  fontweight='bold', color=AGIL['yellow'])
    if 'temperature_moy' in df_daily.columns:
        ax2.plot(df_daily.index, df_daily['temperature_moy'], color=AGIL['amber'], lw=1.5)
        ax2.set_ylabel('Température extérieure (°C)')
        ax2.set_title('Température Extérieure (feature contextuelle)',
                      fontweight='bold', color=AGIL['yellow'])
    ax2.set_xlabel('Date')
    series_path = os.path.join(output_dir, "00_uci_dataset_overview.png")
    fig.tight_layout()
    fig.savefig(series_path, dpi=150, bbox_inches='tight')
    plt.close(fig)
    files.append(series_path)
    print("   ✓ Vue d'ensemble du dataset")

    print(f"\n{'='*60}")
    print(f"  ✅ {len(files)} fichiers → {output_dir}/")
    print(f"{'='*60}\n")

    return {"metrics": all_metrics, "files": files, "output_dir": output_dir}


# =====================================================
# POINT D'ENTRÉE
# =====================================================

if __name__ == "__main__":
    output_dir = "evaluation_uci_themed"
    for arg in sys.argv[1:]:
        if arg.startswith("--output="):
            output_dir = arg.split("=")[1]

    run_uci_evaluation(output_dir)
