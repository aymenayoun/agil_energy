"""
AGIL Energy — Module d'Évaluation des Modèles ML
==================================================
Métriques et visualisations réellement pertinentes pour la prévision
de la demande en carburant (régression + séries temporelles).

Contenu :
─────────
 1. Métriques de régression étendues (MAE, RMSE, MAPE, R², SMAPE, MedAE)
 2. MASE — Mean Absolute Scaled Error (compare au modèle naïf)
 3. Biais de prévision (Forecast Bias) — sur/sous-estimation systématique
 4. Directional Accuracy — le modèle prédit-il correctement la tendance ↑/↓ ?
 5. Courbes de perte train/validation (XGBoost)
 6. Courbe de précision (1 - erreur relative) train/validation
 7. Analyse des résidus (distribution, Q-Q plot, résidus vs prédit, autocorrélation)
 8. Actual vs Predicted (scatter + séries temporelles superposées)
 9. Comparaison des performances des modèles (bar chart)
10. Erreur par jour de semaine et par mois (quand le modèle échoue)
11. Erreur cumulative dans le temps (cumulative forecast error)
12. Cross-validation temporelle (TimeSeriesSplit)
13. Courbes d'apprentissage (Learning Curves)
14. Feature Importance comparative (RF vs XGBoost)
15. Visualisation des intervalles de prédiction P10/P50/P90
16. Autocorrélation des résidus (ACF) — patterns non capturés
17. Évolution des performances (historique des entraînements)
18. Tableau récapitulatif (PNG + CSV)

Usage :
    python -m app.evaluation.model_evaluation
    python -m app.evaluation.model_evaluation --sample
    python -m app.evaluation.model_evaluation --station=1 --fuel=1 --output=evaluation_output
"""

import os
import sys
import logging
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
import seaborn as sns
from datetime import datetime

from sklearn.metrics import (
    mean_absolute_error, mean_squared_error, r2_score,
    explained_variance_score, median_absolute_error,
)
from sklearn.model_selection import TimeSeriesSplit
from sklearn.linear_model import LinearRegression
from sklearn.ensemble import RandomForestRegressor
from xgboost import XGBRegressor

logger = logging.getLogger(__name__)

# ── Style global ──
plt.rcParams.update({
    'figure.figsize': (12, 7),
    'figure.dpi': 150,
    'font.size': 12,
    'axes.titlesize': 14,
    'axes.labelsize': 12,
    'legend.fontsize': 10,
    'figure.facecolor': 'white',
    'axes.facecolor': '#FAFAFA',
    'axes.grid': True,
    'grid.alpha': 0.3,
})

COLORS = {
    'LinearRegression': '#3498DB',
    'RandomForest': '#2ECC71',
    'XGBoost': '#E74C3C',
    'Prophet': '#9B59B6',
    'Ensemble': '#F39C12',
    'XGBoostQuantile': '#1ABC9C',
}


# =====================================================
# 1. MÉTRIQUES DE RÉGRESSION ÉTENDUES
# =====================================================

def compute_extended_metrics(y_true, y_pred, model_name: str = "",
                             y_train: np.ndarray = None) -> dict:
    """
    Métriques complètes et pertinentes pour la régression temporelle :
    - MAE, RMSE, MAPE, SMAPE : erreurs standards
    - R² : pouvoir explicatif
    - Explained Variance : variance capturée
    - MedAE : erreur médiane (robuste aux outliers)
    - Max Error : pire prédiction
    - MASE : comparaison au modèle naïf (lag-1)
    - Forecast Bias : tendance à sur/sous-estimer
    - Directional Accuracy : % de fois où la direction ↑/↓ est correcte
    """
    y_true = np.array(y_true, dtype=float)
    y_pred = np.array(y_pred, dtype=float)

    mae = mean_absolute_error(y_true, y_pred)
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))
    r2 = r2_score(y_true, y_pred)
    evs = explained_variance_score(y_true, y_pred)
    medae = median_absolute_error(y_true, y_pred)
    max_err = float(np.max(np.abs(y_true - y_pred)))

    # MAPE
    mask = y_true != 0
    mape = float(np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask]))) if mask.sum() > 0 else 0.0

    # SMAPE
    denom = (np.abs(y_true) + np.abs(y_pred)) / 2
    denom_safe = np.where(denom == 0, 1, denom)
    smape = float(np.mean(np.abs(y_true - y_pred) / denom_safe))

    # MASE (Mean Absolute Scaled Error) — compare to naive lag-1 forecast
    if y_train is not None and len(y_train) > 1:
        naive_mae = np.mean(np.abs(np.diff(y_train)))
        mase = float(mae / naive_mae) if naive_mae > 0 else float('inf')
    else:
        mase = None

    # Forecast Bias — average signed error (positive = over-predicting)
    bias = float(np.mean(y_pred - y_true))
    bias_pct = float(bias / np.mean(y_true) * 100) if np.mean(y_true) != 0 else 0.0

    # Directional Accuracy — did we predict the right direction of change?
    if len(y_true) > 1:
        actual_dir = np.diff(y_true) > 0
        pred_dir = np.diff(y_pred) > 0
        da = float(np.mean(actual_dir == pred_dir))
    else:
        da = None

    return {
        "model_name": model_name,
        "MAE": round(mae, 4),
        "RMSE": round(rmse, 4),
        "MAPE": round(mape, 4),
        "SMAPE": round(smape, 4),
        "R2": round(r2, 4),
        "Explained_Variance": round(evs, 4),
        "MedAE": round(medae, 4),
        "Max_Error": round(max_err, 4),
        "MASE": round(mase, 4) if mase is not None else None,
        "Forecast_Bias": round(bias, 4),
        "Forecast_Bias_Pct": round(bias_pct, 2),
        "Directional_Accuracy": round(da, 4) if da is not None else None,
        "n_samples": len(y_true),
    }


# =====================================================
# 2. XGBOOST AVEC HISTORIQUE DE PERTE
# =====================================================

def train_xgboost_with_history(X_train, y_train, X_test, y_test) -> tuple:
    """Entraîne XGBoost en capturant les loss curves train/validation."""
    model = XGBRegressor(
        n_estimators=300,
        max_depth=6,
        learning_rate=0.08,
        subsample=0.8,
        colsample_bytree=0.8,
        min_child_weight=3,
        reg_alpha=0.1,
        reg_lambda=1.0,
        random_state=42,
        n_jobs=-1,
        verbosity=0,
        eval_metric=['rmse', 'mae'],
    )
    eval_set = [(X_train, y_train), (X_test, y_test)]
    model.fit(X_train, y_train, eval_set=eval_set, verbose=False)
    return model, model.evals_result()


# =====================================================
# 3. COURBES DE PERTE TRAIN / VALIDATION (XGBoost)
# =====================================================

def plot_loss_curves(eval_results: dict, output_dir: str) -> str:
    """Courbes RMSE et MAE sur entraînement et validation au fil des itérations."""
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))
    epochs = range(len(eval_results['validation_0']['rmse']))

    ax1.plot(epochs, eval_results['validation_0']['rmse'], label='Entraînement', color='#3498DB', lw=2)
    ax1.plot(epochs, eval_results['validation_1']['rmse'], label='Validation', color='#E74C3C', lw=2)
    ax1.set_xlabel('Itérations')
    ax1.set_ylabel('RMSE')
    ax1.set_title('Courbe de Perte (RMSE) — Train vs Validation', fontweight='bold')
    ax1.legend()

    ax2.plot(epochs, eval_results['validation_0']['mae'], label='Entraînement', color='#3498DB', lw=2)
    ax2.plot(epochs, eval_results['validation_1']['mae'], label='Validation', color='#E74C3C', lw=2)
    ax2.set_xlabel('Itérations')
    ax2.set_ylabel('MAE')
    ax2.set_title('Courbe de Perte (MAE) — Train vs Validation', fontweight='bold')
    ax2.legend()

    filepath = os.path.join(output_dir, "01_xgboost_loss_curves.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 4. COURBE DE PRÉCISION TRAIN / VALIDATION
# =====================================================

def plot_accuracy_curves(eval_results: dict, y_mean: float, output_dir: str) -> str:
    """Précision relative (1 - RMSE/mean) au fil des itérations XGBoost."""
    fig, ax = plt.subplots(figsize=(12, 6))
    epochs = range(len(eval_results['validation_0']['rmse']))

    train_acc = [max(0, 1 - rmse / y_mean) for rmse in eval_results['validation_0']['rmse']]
    val_acc = [max(0, 1 - rmse / y_mean) for rmse in eval_results['validation_1']['rmse']]

    ax.plot(epochs, train_acc, label='Entraînement', color='#3498DB', lw=2)
    ax.plot(epochs, val_acc, label='Validation', color='#E74C3C', lw=2)
    ax.set_xlabel('Itérations')
    ax.set_ylabel('Précision relative')
    ax.set_title('Courbe de Précision — XGBoost (Train vs Validation)', fontweight='bold')
    ax.legend()
    ax.yaxis.set_major_formatter(mticker.PercentFormatter(xmax=1.0))
    ax.set_ylim(bottom=0, top=1.05)

    filepath = os.path.join(output_dir, "02_xgboost_accuracy_curve.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 5. ANALYSE DES RÉSIDUS
# =====================================================

def plot_residual_analysis(y_true, y_pred, model_name: str, output_dir: str) -> str:
    """
    4 sous-graphiques :
    - Distribution des résidus (histogramme + KDE)
    - Résidus vs Valeurs prédites (homoscédasticité ?)
    - Q-Q plot (normalité des erreurs ?)
    - Autocorrélation des résidus (ACF) — patterns non capturés ?
    """
    residuals = np.array(y_true) - np.array(y_pred)
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))

    # 1 — Distribution
    ax = axes[0, 0]
    ax.hist(residuals, bins=30, density=True, alpha=0.7, color='#3498DB', edgecolor='white')
    try:
        from scipy.stats import gaussian_kde
        kde = gaussian_kde(residuals)
        x_range = np.linspace(residuals.min(), residuals.max(), 200)
        ax.plot(x_range, kde(x_range), color='#E74C3C', lw=2, label='KDE')
    except Exception:
        pass
    ax.axvline(0, color='black', linestyle='--', lw=1)
    mean_res = np.mean(residuals)
    ax.axvline(mean_res, color='orange', linestyle=':', lw=1.5, label=f'Moyenne = {mean_res:.2f}')
    ax.set_xlabel('Résidu (Réel − Prédit)')
    ax.set_ylabel('Densité')
    ax.set_title('Distribution des Résidus', fontweight='bold')
    ax.legend(fontsize=9)

    # 2 — Résidus vs Prédictions
    ax = axes[0, 1]
    ax.scatter(y_pred, residuals, alpha=0.5, s=20, color='#3498DB')
    ax.axhline(0, color='red', linestyle='--', lw=1)
    # Smooth trend line
    try:
        z = np.polyfit(y_pred, residuals, 2)
        p = np.poly1d(z)
        x_sorted = np.sort(y_pred)
        ax.plot(x_sorted, p(x_sorted), color='orange', lw=2, label='Tendance')
        ax.legend(fontsize=9)
    except Exception:
        pass
    ax.set_xlabel('Valeurs Prédites')
    ax.set_ylabel('Résidus')
    ax.set_title('Résidus vs Prédictions (Homoscédasticité)', fontweight='bold')

    # 3 — Q-Q Plot
    ax = axes[1, 0]
    from scipy import stats
    sorted_res = np.sort(residuals)
    n = len(sorted_res)
    theoretical = stats.norm.ppf(np.linspace(1/(n+1), n/(n+1), n))
    ax.scatter(theoretical, sorted_res, alpha=0.6, s=15, color='#2ECC71')
    lim_min = min(theoretical.min(), sorted_res.min())
    lim_max = max(theoretical.max(), sorted_res.max())
    ax.plot([lim_min, lim_max], [lim_min, lim_max], 'r--', lw=1, label='Distribution normale')
    ax.set_xlabel('Quantiles Théoriques (Loi Normale)')
    ax.set_ylabel('Quantiles Observés')
    ax.set_title('Q-Q Plot — Normalité des Résidus', fontweight='bold')
    ax.legend(fontsize=9)

    # 4 — Autocorrélation des résidus (ACF)
    ax = axes[1, 1]
    max_lag = min(20, len(residuals) // 3)
    acf_vals = []
    for lag in range(max_lag + 1):
        if lag == 0:
            acf_vals.append(1.0)
        else:
            c = np.corrcoef(residuals[lag:], residuals[:-lag])[0, 1]
            acf_vals.append(c if not np.isnan(c) else 0)
    ax.bar(range(max_lag + 1), acf_vals, color='#9B59B6', alpha=0.7, edgecolor='white')
    # Seuil de significativité (±1.96/√n)
    sig = 1.96 / np.sqrt(len(residuals))
    ax.axhline(sig, color='red', linestyle='--', lw=1, alpha=0.7, label=f'Seuil ±{sig:.3f}')
    ax.axhline(-sig, color='red', linestyle='--', lw=1, alpha=0.7)
    ax.axhline(0, color='black', lw=0.5)
    ax.set_xlabel('Lag (décalage)')
    ax.set_ylabel('Autocorrélation')
    ax.set_title('ACF des Résidus — Patterns Non Capturés', fontweight='bold')
    ax.legend(fontsize=9)

    fig.suptitle(f'Analyse des Résidus — {model_name}', fontweight='bold', fontsize=16, y=1.02)
    filepath = os.path.join(output_dir, f"03_residual_analysis_{model_name}.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 6. ACTUAL vs PREDICTED
# =====================================================

def plot_actual_vs_predicted(y_true, predictions_dict: dict, output_dir: str,
                              dates=None) -> str:
    """
    Deux vues :
    - Scatter plot avec ligne y=x et R² annoté
    - Séries temporelles superposées (réel vs chaque modèle)
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))
    y_true_arr = np.array(y_true)
    x_axis = dates if dates is not None else range(len(y_true_arr))

    # Scatter
    for model_name, y_pred in predictions_dict.items():
        color = COLORS.get(model_name, '#333333')
        r2 = r2_score(y_true_arr, y_pred)
        ax1.scatter(y_true_arr, y_pred, alpha=0.5, s=20, color=color,
                    label=f'{model_name} (R²={r2:.3f})')

    v_min = min(y_true_arr.min(), min(np.array(p).min() for p in predictions_dict.values()))
    v_max = max(y_true_arr.max(), max(np.array(p).max() for p in predictions_dict.values()))
    ax1.plot([v_min, v_max], [v_min, v_max], 'k--', lw=1, label='Prédiction parfaite')
    ax1.set_xlabel('Valeurs Réelles')
    ax1.set_ylabel('Valeurs Prédites')
    ax1.set_title('Réel vs Prédit', fontweight='bold')
    ax1.legend(fontsize=8)

    # Time series
    ax2.plot(x_axis, y_true_arr, 'k-', lw=2.5, label='Réel', alpha=0.9)
    for model_name, y_pred in predictions_dict.items():
        color = COLORS.get(model_name, '#333333')
        ax2.plot(x_axis, y_pred, '--', color=color, lw=1.5, label=model_name, alpha=0.7)
    ax2.set_xlabel('Date' if dates is not None else 'Index temporel')
    ax2.set_ylabel('Quantité (L)')
    ax2.set_title('Séries Temporelles — Réel vs Prédictions', fontweight='bold')
    ax2.legend(fontsize=8)

    filepath = os.path.join(output_dir, "04_actual_vs_predicted.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 7. COMPARAISON DES PERFORMANCES (BAR CHART)
# =====================================================

def plot_model_comparison(all_metrics: list, output_dir: str) -> str:
    """Bar chart comparant les métriques clés de tous les modèles."""
    df = pd.DataFrame(all_metrics)
    models = df['model_name'].tolist()

    fig, axes = plt.subplots(2, 3, figsize=(18, 10))
    metrics_to_plot = [
        ('MAE', 'MAE (Erreur Absolue Moyenne)', axes[0, 0], False),
        ('RMSE', 'RMSE (Racine Erreur Quadratique)', axes[0, 1], False),
        ('MAPE', 'MAPE (Erreur % Absolue Moyenne)', axes[0, 2], False),
        ('R2', 'R² (Coefficient de Détermination)', axes[1, 0], True),
        ('MASE', 'MASE (vs Modèle Naïf)', axes[1, 1], False),
        ('Directional_Accuracy', 'Directional Accuracy (%)', axes[1, 2], True),
    ]

    for metric_key, title, ax, higher_is_better in metrics_to_plot:
        values = df[metric_key].values
        if any(v is None for v in values):
            values = np.array([v if v is not None else 0 for v in values])
        colors = [COLORS.get(m, '#333333') for m in models]
        bars = ax.bar(models, values, color=colors, edgecolor='white', lw=1.5)

        for bar, val in zip(bars, values):
            ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height(),
                    f'{val:.4f}', ha='center', va='bottom', fontsize=9, fontweight='bold')

        # Highlight best
        if higher_is_better:
            best_idx = np.argmax(values)
        else:
            best_idx = np.argmin(values[values > 0]) if any(values > 0) else 0
        bars[best_idx].set_edgecolor('#F39C12')
        bars[best_idx].set_linewidth(3)

        ax.set_title(title, fontweight='bold')
        ax.tick_params(axis='x', rotation=25)

    fig.suptitle('Comparaison des Performances des Modèles',
                 fontweight='bold', fontsize=16, y=1.02)
    filepath = os.path.join(output_dir, "05_model_comparison.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 8. ERREUR PAR JOUR DE SEMAINE ET PAR MOIS
# =====================================================

def plot_error_by_period(y_true, predictions_dict: dict, dates,
                          output_dir: str) -> str:
    """
    Identifie quand les modèles sont les moins performants :
    - MAE par jour de semaine (Lundi–Dimanche)
    - MAE par mois
    """
    dates = pd.to_datetime(dates)
    y_true_arr = np.array(y_true)

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))
    day_names = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim']
    month_names = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Jun',
                   'Jul', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc']

    x_days = np.arange(7)
    x_months = np.arange(12)
    width = 0.8 / len(predictions_dict)

    for i, (model_name, y_pred) in enumerate(predictions_dict.items()):
        errors = np.abs(y_true_arr - np.array(y_pred))
        color = COLORS.get(model_name, '#333333')

        # By day of week
        day_errors = [errors[dates.dayofweek == d].mean() if (dates.dayofweek == d).any() else 0
                      for d in range(7)]
        ax1.bar(x_days + i * width, day_errors, width, label=model_name, color=color, alpha=0.8)

        # By month
        month_errors = [errors[dates.month == m].mean() if (dates.month == m).any() else 0
                        for m in range(1, 13)]
        ax2.bar(x_months + i * width, month_errors, width, label=model_name, color=color, alpha=0.8)

    ax1.set_xticks(x_days + width * len(predictions_dict) / 2)
    ax1.set_xticklabels(day_names)
    ax1.set_ylabel('MAE Moyenne')
    ax1.set_title('Erreur Moyenne par Jour de Semaine', fontweight='bold')
    ax1.legend(fontsize=8)

    ax2.set_xticks(x_months + width * len(predictions_dict) / 2)
    ax2.set_xticklabels(month_names, rotation=45)
    ax2.set_ylabel('MAE Moyenne')
    ax2.set_title('Erreur Moyenne par Mois', fontweight='bold')
    ax2.legend(fontsize=8)

    filepath = os.path.join(output_dir, "06_error_by_period.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 9. ERREUR CUMULATIVE DANS LE TEMPS
# =====================================================

def plot_cumulative_error(y_true, predictions_dict: dict, output_dir: str,
                           dates=None) -> str:
    """
    Montre comment l'erreur s'accumule au fil du temps.
    Un modèle biaisé aura une pente constante ; un bon modèle oscillera autour de 0.
    """
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 9), sharex=True)
    y_true_arr = np.array(y_true)
    x_axis = dates if dates is not None else range(len(y_true_arr))

    for model_name, y_pred in predictions_dict.items():
        color = COLORS.get(model_name, '#333333')
        errors_signed = np.array(y_pred) - y_true_arr
        cum_error = np.cumsum(errors_signed)
        cum_abs_error = np.cumsum(np.abs(errors_signed))

        ax1.plot(x_axis, cum_error, color=color, lw=2, label=model_name)
        ax2.plot(x_axis, cum_abs_error, color=color, lw=2, label=model_name)

    ax1.axhline(0, color='black', lw=0.5)
    ax1.set_ylabel('Erreur Cumulative Signée')
    ax1.set_title('Erreur Cumulative Signée (Biais Visible)', fontweight='bold')
    ax1.legend(fontsize=9)

    ax2.set_ylabel('Erreur Cumulative Absolue')
    ax2.set_xlabel('Date' if dates is not None else 'Index temporel')
    ax2.set_title('Erreur Cumulative Absolue', fontweight='bold')
    ax2.legend(fontsize=9)

    filepath = os.path.join(output_dir, "07_cumulative_error.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 10. CROSS-VALIDATION TEMPORELLE
# =====================================================

def plot_cross_validation(X, y, output_dir: str, n_splits: int = 5) -> str:
    """TimeSeriesSplit : box plots des scores par fold pour chaque modèle."""
    tscv = TimeSeriesSplit(n_splits=n_splits)

    models = {
        'LinearRegression': LinearRegression(),
        'RandomForest': RandomForestRegressor(n_estimators=50, max_depth=8,
                                               random_state=42, n_jobs=-1),
        'XGBoost': XGBRegressor(n_estimators=100, max_depth=5, learning_rate=0.1,
                                 random_state=42, n_jobs=-1, verbosity=0),
    }

    results = {name: {'MAE': [], 'RMSE': [], 'R2': []} for name in models}

    for train_idx, test_idx in tscv.split(X):
        X_tr, X_te = X.iloc[train_idx], X.iloc[test_idx]
        y_tr, y_te = y.iloc[train_idx], y.iloc[test_idx]

        for name, model in models.items():
            m = model.__class__(**model.get_params())
            m.fit(X_tr, y_tr)
            pred = np.maximum(m.predict(X_te), 0)
            results[name]['MAE'].append(mean_absolute_error(y_te, pred))
            results[name]['RMSE'].append(np.sqrt(mean_squared_error(y_te, pred)))
            results[name]['R2'].append(r2_score(y_te, pred))

    fig, axes = plt.subplots(1, 3, figsize=(18, 6))
    for i, metric in enumerate(['MAE', 'RMSE', 'R2']):
        ax = axes[i]
        data = [results[n][metric] for n in models]
        bp = ax.boxplot(data, labels=list(models.keys()), patch_artist=True)
        for j, (patch, name) in enumerate(zip(bp['boxes'], models.keys())):
            patch.set_facecolor(COLORS.get(name, '#CCC'))
            patch.set_alpha(0.7)
        ax.set_title(f'Cross-Validation — {metric}', fontweight='bold')
        ax.set_ylabel(metric)
        ax.tick_params(axis='x', rotation=25)

    fig.suptitle(f'Validation Croisée Temporelle ({n_splits} folds — TimeSeriesSplit)',
                 fontweight='bold', fontsize=14, y=1.02)
    filepath = os.path.join(output_dir, "08_cross_validation.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath, results


# =====================================================
# 11. COURBES D'APPRENTISSAGE
# =====================================================

def plot_learning_curves(X, y, output_dir: str) -> str:
    """Performance vs taille du jeu d'entraînement — diagnostic sur/sous-apprentissage."""
    from sklearn.model_selection import learning_curve

    models = {
        'LinearRegression': LinearRegression(),
        'RandomForest': RandomForestRegressor(n_estimators=50, max_depth=8,
                                               random_state=42, n_jobs=-1),
        'XGBoost': XGBRegressor(n_estimators=100, max_depth=5, learning_rate=0.1,
                                 random_state=42, n_jobs=-1, verbosity=0),
    }

    fig, axes = plt.subplots(1, 3, figsize=(18, 5))
    for ax, (name, model) in zip(axes, models.items()):
        train_sizes, train_scores, val_scores = learning_curve(
            model, X, y,
            cv=TimeSeriesSplit(n_splits=3),
            train_sizes=np.linspace(0.2, 1.0, 8),
            scoring='neg_mean_absolute_error',
            n_jobs=-1
        )
        train_mean = -train_scores.mean(axis=1)
        train_std = train_scores.std(axis=1)
        val_mean = -val_scores.mean(axis=1)
        val_std = val_scores.std(axis=1)

        ax.plot(train_sizes, train_mean, 'o-', color='#3498DB', label='Entraînement', lw=2)
        ax.fill_between(train_sizes, train_mean - train_std, train_mean + train_std,
                        alpha=0.1, color='#3498DB')
        ax.plot(train_sizes, val_mean, 's-', color='#E74C3C', label='Validation', lw=2)
        ax.fill_between(train_sizes, val_mean - val_std, val_mean + val_std,
                        alpha=0.1, color='#E74C3C')
        ax.set_xlabel('Taille du jeu d\'entraînement')
        ax.set_ylabel('MAE')
        ax.set_title(f'Courbe d\'Apprentissage — {name}', fontweight='bold')
        ax.legend()

    filepath = os.path.join(output_dir, "09_learning_curves.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 12. FEATURE IMPORTANCE COMPARATIVE
# =====================================================

def plot_feature_importance_comparison(models_dict: dict, feature_names: list,
                                       output_dir: str, top_n: int = 15) -> str:
    """Importances côte à côte pour RF et XGBoost."""
    n_models = len(models_dict)
    fig, axes = plt.subplots(1, n_models, figsize=(8 * n_models, 8))
    if n_models == 1:
        axes = [axes]

    for ax, (model_name, model) in zip(axes, models_dict.items()):
        if hasattr(model, 'feature_importances_'):
            imp = model.feature_importances_
            indices = np.argsort(imp)[-top_n:]
            color = COLORS.get(model_name, '#333333')
            ax.barh(range(len(indices)), imp[indices], color=color, alpha=0.8)
            ax.set_yticks(range(len(indices)))
            ax.set_yticklabels([feature_names[i] for i in indices])
            ax.set_xlabel('Importance')
            ax.set_title(f'Feature Importance — {model_name}', fontweight='bold')

    filepath = os.path.join(output_dir, "10_feature_importance.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 13. INTERVALLES DE PRÉDICTION P10/P50/P90
# =====================================================

def plot_prediction_intervals(y_true, p10, p50, p90, output_dir: str,
                               dates=None) -> str:
    """Visualise les intervalles de prédiction avec la couverture réelle."""
    fig, ax = plt.subplots(figsize=(14, 6))
    y_true_arr = np.array(y_true)
    p10_arr, p50_arr, p90_arr = np.array(p10), np.array(p50), np.array(p90)
    x = dates if dates is not None else range(len(y_true_arr))

    ax.fill_between(x, p10_arr, p90_arr, alpha=0.25, color='#3498DB', label='Intervalle P10–P90')
    ax.plot(x, p50_arr, '-', color='#2C3E50', lw=2, label='Médiane (P50)')
    ax.plot(x, y_true_arr, 'o-', color='#E74C3C', lw=1.5, markersize=4, label='Réel')

    outside = (y_true_arr < p10_arr) | (y_true_arr > p90_arr)
    if outside.any():
        out_x = np.array(x)[outside] if dates is not None else np.where(outside)[0]
        ax.scatter(out_x, y_true_arr[outside], color='#F39C12', s=80, zorder=5,
                   marker='x', label='Hors intervalle')

    coverage = float((~outside).sum() / len(y_true_arr) * 100)
    ax.set_xlabel('Date' if dates is not None else 'Index')
    ax.set_ylabel('Quantité (L)')
    ax.set_title(f'Intervalles de Prédiction — Couverture réelle : {coverage:.1f}%', fontweight='bold')
    ax.legend()

    filepath = os.path.join(output_dir, "11_prediction_intervals.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 14. ÉVOLUTION DES PERFORMANCES (historique BD)
# =====================================================

def plot_performance_evolution(metrics_history: list, output_dir: str) -> str:
    """MAPE et RMSE de chaque modèle au fil des ré-entraînements."""
    if not metrics_history:
        return None

    df = pd.DataFrame(metrics_history)
    df['created_at'] = pd.to_datetime(df['created_at'])

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10), sharex=True)
    for model_name in df['model_name'].unique():
        md = df[df['model_name'] == model_name].sort_values('created_at')
        color = COLORS.get(model_name, '#333333')
        ax1.plot(md['created_at'], md['mape'], 'o-', label=model_name, color=color, lw=2, markersize=5)
        ax2.plot(md['created_at'], md['rmse'], 's-', label=model_name, color=color, lw=2, markersize=5)

    ax1.set_ylabel('MAPE')
    ax1.set_title('Évolution du MAPE au Fil des Entraînements', fontweight='bold')
    ax1.legend()
    ax2.set_ylabel('RMSE')
    ax2.set_xlabel('Date d\'entraînement')
    ax2.set_title('Évolution du RMSE au Fil des Entraînements', fontweight='bold')
    ax2.legend()

    filepath = os.path.join(output_dir, "12_performance_evolution.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 15. FORECAST BIAS VISUALIZATION
# =====================================================

def plot_forecast_bias(y_true, predictions_dict: dict, output_dir: str) -> str:
    """
    Visualise le biais de prévision :
    - Barre : erreur moyenne signée par modèle (>0 = sur-estimation)
    - Histogramme : distribution des erreurs signées
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))
    y_true_arr = np.array(y_true)

    # Bar — mean signed error
    biases = []
    names = []
    colors = []
    for model_name, y_pred in predictions_dict.items():
        bias = np.mean(np.array(y_pred) - y_true_arr)
        biases.append(bias)
        names.append(model_name)
        colors.append(COLORS.get(model_name, '#333333'))

    bars = ax1.bar(names, biases, color=colors, edgecolor='white', lw=1.5)
    ax1.axhline(0, color='black', lw=0.8)
    for bar, val in zip(bars, biases):
        sign = '+' if val > 0 else ''
        ax1.text(bar.get_x() + bar.get_width() / 2, bar.get_height(),
                 f'{sign}{val:.2f}', ha='center', va='bottom' if val >= 0 else 'top',
                 fontsize=10, fontweight='bold')
    ax1.set_ylabel('Biais Moyen (Prédit − Réel)')
    ax1.set_title('Biais de Prévision par Modèle', fontweight='bold')
    ax1.tick_params(axis='x', rotation=25)

    # Histogram — distribution of signed errors
    for model_name, y_pred in predictions_dict.items():
        signed_err = np.array(y_pred) - y_true_arr
        color = COLORS.get(model_name, '#333333')
        ax2.hist(signed_err, bins=25, alpha=0.4, color=color, label=model_name, edgecolor='white')
    ax2.axvline(0, color='black', linestyle='--', lw=1)
    ax2.set_xlabel('Erreur Signée (Prédit − Réel)')
    ax2.set_ylabel('Fréquence')
    ax2.set_title('Distribution des Erreurs Signées', fontweight='bold')
    ax2.legend(fontsize=9)

    filepath = os.path.join(output_dir, "13_forecast_bias.png")
    fig.tight_layout()
    fig.savefig(filepath, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return filepath


# =====================================================
# 16. TABLEAU RÉCAPITULATIF (PNG + CSV)
# =====================================================

def save_metrics_table(all_metrics: list, output_dir: str) -> tuple:
    """Sauvegarde le tableau complet en CSV et en image annotée."""
    df = pd.DataFrame(all_metrics)
    csv_path = os.path.join(output_dir, "metrics_summary.csv")
    df.to_csv(csv_path, index=False)

    cols = ['model_name', 'MAE', 'RMSE', 'MAPE', 'R2', 'MASE',
            'Forecast_Bias_Pct', 'Directional_Accuracy']
    headers = ['Modèle', 'MAE', 'RMSE', 'MAPE', 'R²', 'MASE',
               'Biais (%)', 'Dir. Acc.']
    display_df = df[cols].copy()
    display_df.columns = headers

    fig, ax = plt.subplots(figsize=(18, 2.5 + len(df) * 0.6))
    ax.axis('off')

    cell_text = []
    for _, row in display_df.iterrows():
        cell_text.append([
            row['Modèle'],
            f"{row['MAE']:.4f}",
            f"{row['RMSE']:.4f}",
            f"{row['MAPE']:.4f}",
            f"{row['R²']:.4f}",
            f"{row['MASE']:.4f}" if row['MASE'] is not None else "—",
            f"{row['Biais (%)']:.2f}%",
            f"{row['Dir. Acc.']:.2%}" if row['Dir. Acc.'] is not None else "—",
        ])

    table = ax.table(cellText=cell_text, colLabels=headers, cellLoc='center', loc='center')
    table.auto_set_font_size(False)
    table.set_fontsize(10)
    table.scale(1.2, 1.8)

    for (row, col), cell in table.get_celld().items():
        if row == 0:
            cell.set_facecolor('#2C3E50')
            cell.set_text_props(color='white', fontweight='bold')

    ax.set_title('Tableau Récapitulatif des Métriques de Performance',
                 fontweight='bold', fontsize=14, pad=20)
    img_path = os.path.join(output_dir, "00_metrics_table.png")
    fig.savefig(img_path, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return csv_path, img_path


# =====================================================
# PIPELINE COMPLET
# =====================================================

def run_full_evaluation(station_id: int = 1, fuel_type_id: int = 1,
                         output_dir: str = "evaluation_output",
                         use_sample_data: bool = False) -> dict:
    """
    Exécute toutes les évaluations et génère tous les graphiques.
    """
    os.makedirs(output_dir, exist_ok=True)
    files = []
    all_metrics = []

    print(f"\n{'='*60}")
    print(f"  ÉVALUATION COMPLÈTE DES MODÈLES ML")
    print(f"  Station: {station_id} | Carburant: {fuel_type_id}")
    print(f"{'='*60}\n")

    # ── 1. Données ──
    if use_sample_data:
        print("[1] Données d'exemple (--sample)...")
        df_raw, df_features, X_train, y_train, X_test, y_test = _generate_sample_data()
    else:
        print("[1] Chargement depuis la BD...")
        from app.services.database import fetch_sales_data
        from app.services.feature_engineering import create_features, prepare_train_test
        df_raw = fetch_sales_data(station_id, fuel_type_id)
        if df_raw.empty or len(df_raw) < 30:
            print(f"   ⚠ Seulement {len(df_raw)} jours → données d'exemple")
            df_raw, df_features, X_train, y_train, X_test, y_test = _generate_sample_data()
        else:
            df_features = create_features(df_raw, station_id=station_id, fuel_type_id=fuel_type_id)
            test_days = min(14, len(df_features) // 5)
            X_train, y_train, X_test, y_test = prepare_train_test(df_features, test_days)

    feature_cols = list(X_train.columns)
    test_dates = X_test.index
    print(f"   {len(X_train)} train / {len(X_test)} test / {len(feature_cols)} features")

    # ── 2. Entraînement ──
    print("\n[2] Entraînement des modèles...")
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
    print("   ✓ LR, RF, XGBoost entraînés")

    # ── 3. Métriques ──
    print("\n[3] Métriques étendues...")
    y_train_arr = np.array(y_train)
    for name, pred in preds.items():
        m = compute_extended_metrics(y_test, pred, name, y_train_arr)
        all_metrics.append(m)
        print(f"   {name}: MAE={m['MAE']} | R²={m['R2']} | MASE={m['MASE']} | "
              f"Biais={m['Forecast_Bias_Pct']}% | DirAcc={m['Directional_Accuracy']}")

    # ── 4. Générations des graphiques ──
    print("\n[4] Génération des graphiques...\n")

    csv_path, img = save_metrics_table(all_metrics, output_dir)
    files.extend([csv_path, img])
    print(f"   ✓ Tableau récapitulatif")

    files.append(plot_loss_curves(xgb_eval, output_dir))
    print(f"   ✓ Courbes de perte XGBoost")

    files.append(plot_accuracy_curves(xgb_eval, float(y_test.mean()), output_dir))
    print(f"   ✓ Courbe de précision XGBoost")

    for name, pred in preds.items():
        files.append(plot_residual_analysis(y_test, pred, name, output_dir))
    print(f"   ✓ Analyse des résidus (×{len(preds)} modèles)")

    files.append(plot_actual_vs_predicted(y_test, preds, output_dir, dates=test_dates))
    print(f"   ✓ Actual vs Predicted")

    files.append(plot_model_comparison(all_metrics, output_dir))
    print(f"   ✓ Comparaison des modèles")

    files.append(plot_error_by_period(y_test, preds, test_dates, output_dir))
    print(f"   ✓ Erreur par jour/mois")

    files.append(plot_cumulative_error(y_test, preds, output_dir, dates=test_dates))
    print(f"   ✓ Erreur cumulative")

    files.append(plot_forecast_bias(y_test, preds, output_dir))
    print(f"   ✓ Biais de prévision")

    X_all = pd.concat([X_train, X_test])
    y_all = pd.concat([y_train, y_test])

    cv_path, cv_results = plot_cross_validation(X_all, y_all, output_dir)
    files.append(cv_path)
    print(f"   ✓ Cross-validation temporelle")

    files.append(plot_learning_curves(X_all, y_all, output_dir))
    print(f"   ✓ Courbes d'apprentissage")

    files.append(plot_feature_importance_comparison(
        {'RandomForest': rf, 'XGBoost': xgb}, feature_cols, output_dir))
    print(f"   ✓ Feature importance")

    # Performance evolution (if DB metrics exist)
    try:
        if not use_sample_data:
            from app.services.database import fetch_model_metrics
            history = fetch_model_metrics(station_id, fuel_type_id, limit=100)
            if history:
                f = plot_performance_evolution(history, output_dir)
                if f:
                    files.append(f)
                    print(f"   ✓ Évolution des performances")
    except Exception:
        pass

    print(f"\n{'='*60}")
    print(f"  ✅ {len(files)} fichiers → {output_dir}/")
    print(f"{'='*60}\n")

    return {"metrics": all_metrics, "files": files, "output_dir": output_dir}


# =====================================================
# DONNÉES D'EXEMPLE
# =====================================================

def _generate_sample_data():

    np.random.seed(42)
    dates = pd.date_range(start='2024-01-01', end='2025-12-31', freq='D')
    n = len(dates)

    trend = np.linspace(1000, 1100, n)
    weekly = 200 * np.sin(2 * np.pi * np.arange(n) / 7)
    yearly = 150 * np.sin(2 * np.pi * np.arange(n) / 365)
    noise = np.random.normal(0, 80, n)
    quantity = np.maximum(trend + weekly + yearly + noise, 50)

    df_raw = pd.DataFrame({'quantity': quantity}, index=dates)
    data = df_raw.copy()
    data['day_of_week'] = data.index.dayofweek
    data['month'] = data.index.month
    data['quarter'] = data.index.quarter
    data['is_weekend'] = (data.index.dayofweek >= 5).astype(int)
    data['day_of_month'] = data.index.day
    data['day_sin'] = np.sin(2 * np.pi * data['day_of_week'] / 7)
    data['day_cos'] = np.cos(2 * np.pi * data['day_of_week'] / 7)
    data['month_sin'] = np.sin(2 * np.pi * data['month'] / 12)
    data['month_cos'] = np.cos(2 * np.pi * data['month'] / 12)
    data['lag_1'] = data['quantity'].shift(1)
    data['lag_7'] = data['quantity'].shift(7)
    data['lag_14'] = data['quantity'].shift(14)

    # Statistiques de fenêtre sur quantity décalé de 1 (pas de fuite)
    q_past = data['quantity'].shift(1)
    data['rolling_mean_7'] = q_past.rolling(7).mean()
    data['rolling_mean_14'] = q_past.rolling(14).mean()
    data['rolling_std_7'] = q_past.rolling(7).std()
    data['rolling_std_14'] = q_past.rolling(14).std()

    # ===== daily_change / daily_diff : SUPPRIMÉES (fuite de cible) =====

    data['ratio_7_14'] = data['rolling_mean_7'] / data['rolling_mean_14'].replace(0, np.nan)
    data = data.dropna()

    test_days = 14
    fc = [c for c in data.columns if c != 'quantity']
    X_train = data.iloc[:-test_days][fc]
    y_train = data.iloc[:-test_days]['quantity']
    X_test = data.iloc[-test_days:][fc]
    y_test = data.iloc[-test_days:]['quantity']
    return df_raw, data, X_train, y_train, X_test, y_test


# =====================================================
# POINT D'ENTRÉE
# =====================================================

if __name__ == "__main__":
    use_sample = "--sample" in sys.argv
    station_id, fuel_type_id = 1, 1
    output_dir = "evaluation_output"

    for arg in sys.argv[1:]:
        if arg.startswith("--station="):
            station_id = int(arg.split("=")[1])
        elif arg.startswith("--fuel="):
            fuel_type_id = int(arg.split("=")[1])
        elif arg.startswith("--output="):
            output_dir = arg.split("=")[1]

    if not use_sample:
        try:
            from app.services.database import get_connection
            conn = get_connection()
            conn.close()
        except Exception as e:
            print(f"⚠ BD inaccessible ({e}) → --sample")
            use_sample = True

    run_full_evaluation(station_id, fuel_type_id, output_dir, use_sample)
