import pandas as pd
import numpy as np
from app.services.external_features import enrich_features



def create_features(df: pd.DataFrame, station_id=None, fuel_type_id=None) -> pd.DataFrame:
    data = df.copy()

    # ===== Variables Temporelles ===== (connues d'avance, aucune fuite)
    data['day_of_week'] = data.index.dayofweek          # 0=Lundi, 6=Dimanche
    data['month'] = data.index.month
    data['quarter'] = data.index.quarter
    data['is_weekend'] = (data.index.dayofweek >= 5).astype(int)
    data['day_of_month'] = data.index.day

    # Encodage cyclique sin/cos (capture la périodicité)
    data['day_sin'] = np.sin(2 * np.pi * data['day_of_week'] / 7)
    data['day_cos'] = np.cos(2 * np.pi * data['day_of_week'] / 7)
    data['month_sin'] = np.sin(2 * np.pi * data['month'] / 12)
    data['month_cos'] = np.cos(2 * np.pi * data['month'] / 12)

    # ===== Lag Features ===== (valeurs strictement passées — OK)
    data['lag_1'] = data['quantity'].shift(1)            # J-1
    data['lag_7'] = data['quantity'].shift(7)            # J-7
    data['lag_14'] = data['quantity'].shift(14)          # J-14

    # ===== Rolling Statistics =====
    # CORRECTIF FUITE : .rolling() est aligné à DROITE et INCLUT la ligne
    # courante quantity[i]. Pour prédire quantity[i] on ne doit utiliser que
    # quantity[i-1], quantity[i-2], ...  → on applique .shift(1) à TOUTES les
    # statistiques de fenêtre. La fenêtre se termine donc en i-1.
    q_past = data['quantity'].shift(1)
    data['rolling_mean_7'] = q_past.rolling(window=7).mean()
    data['rolling_mean_14'] = q_past.rolling(window=14).mean()
    data['rolling_std_7'] = q_past.rolling(window=7).std()
    data['rolling_std_14'] = q_past.rolling(window=14).std()

    # ===== daily_change / daily_diff / ewm_ratio : SUPPRIMÉES (fuite de cible) =====

    # ===== Ratio et Tendance =====
    data['ratio_7_14'] = data['rolling_mean_7'] / data['rolling_mean_14'].replace(0, np.nan)

    # Pente de tendance sur 7 jours (régression linéaire locale)
    # CORRECTIF FUITE : la pente est calculée sur q_past (décalé de 1), donc
    # la fenêtre [i-7 .. i-1] n'inclut JAMAIS quantity[i].
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

    # Moyenne mobile exponentielle (décalée de 1 → ne voit pas quantity[i])
    data['ewm_7'] = q_past.ewm(span=7).mean()

    # Enrichissement features externes (météo, fériés, Ramadan, etc.)
    if station_id is not None:
        data = enrich_features(data, station_id, fuel_type_id)

    # Supprimer les lignes avec NaN (dues aux lag/rolling/shift)
    data = data.dropna()
    return data


def prepare_train_test(data: pd.DataFrame, test_days: int = 14):
    """
    Séparation temporelle train/test (TimeSeriesSplit).
    Les 'test_days' derniers jours sont utilisés pour la validation.
    """
    if len(data) <= test_days:
        return data, pd.DataFrame(), [], []

    train = data.iloc[:-test_days]
    test = data.iloc[-test_days:]

    feature_cols = [c for c in data.columns if c != 'quantity']

    X_train = train[feature_cols]
    y_train = train['quantity']
    X_test = test[feature_cols]
    y_test = test['quantity']

    return X_train, y_train, X_test, y_test


def prepare_future_features(df: pd.DataFrame, forecast_days: int = 7,
                            station_id: int = None, fuel_type_id: int = None) -> pd.DataFrame:
    """
    Créer les features pour les jours futurs à prédire.
    Utilise les dernières valeurs connues pour les lags et rolling stats.
    Enrichit avec les features externes (météo, fériés, etc.) si disponibles.

    NOTE : les 3 features supprimées dans create_features (daily_diff,
    daily_change, ewm_ratio) ne sont plus générées ici non plus, pour garantir
    que les colonnes train et futures soient strictement identiques.
    """
    last_date = df.index[-1]
    future_dates = pd.date_range(start=last_date + pd.Timedelta(days=1), periods=forecast_days, freq='D')

    future_data = []
    extended = df.copy()

    for date in future_dates:
        row = {}
        row['day_of_week'] = date.dayofweek
        row['month'] = date.month
        row['quarter'] = date.quarter
        row['is_weekend'] = 1 if date.dayofweek >= 5 else 0
        row['day_of_month'] = date.day
        row['day_sin'] = np.sin(2 * np.pi * date.dayofweek / 7)
        row['day_cos'] = np.cos(2 * np.pi * date.dayofweek / 7)
        row['month_sin'] = np.sin(2 * np.pi * date.month / 12)
        row['month_cos'] = np.cos(2 * np.pi * date.month / 12)

        recent = extended['quantity']
        row['lag_1'] = recent.iloc[-1] if len(recent) >= 1 else 0
        row['lag_7'] = recent.iloc[-7] if len(recent) >= 7 else recent.mean()
        row['lag_14'] = recent.iloc[-14] if len(recent) >= 14 else recent.mean()

        row['rolling_mean_7'] = recent.iloc[-7:].mean() if len(recent) >= 7 else recent.mean()
        row['rolling_mean_14'] = recent.iloc[-14:].mean() if len(recent) >= 14 else recent.mean()
        row['rolling_std_7'] = recent.iloc[-7:].std() if len(recent) >= 7 else recent.std()
        row['rolling_std_14'] = recent.iloc[-14:].std() if len(recent) >= 14 else recent.std()

        # ===== CORRECTIF FUITE : daily_change et daily_diff SUPPRIMÉES =====
        # (anciennement calculées ici ; retirées pour cohérence avec create_features.)

        rm7 = row['rolling_mean_7']
        rm14 = row['rolling_mean_14']
        row['ratio_7_14'] = rm7 / rm14 if rm14 != 0 else 1.0

        # Trend slope: pente linéaire sur les 7 derniers jours
        recent_7 = extended['quantity'].iloc[-7:].values if len(extended) >= 7 else extended['quantity'].values
        if len(recent_7) >= 2:
            x = np.arange(len(recent_7))
            row['trend_slope_7'] = float(np.polyfit(x, recent_7, 1)[0])
        else:
            row['trend_slope_7'] = 0.0

        # EWM
        ewm_val = extended['quantity'].ewm(span=7).mean().iloc[-1]
        row['ewm_7'] = float(ewm_val)

        # ===== CORRECTIF FUITE : ewm_ratio SUPPRIMÉE =====
        # (anciennement : row['ewm_ratio'] = recent.iloc[-1] / ewm_val ; retirée.)

        future_data.append(row)

        estimated_qty = row['rolling_mean_7']
        new_row = pd.DataFrame({'quantity': [estimated_qty]}, index=[date])
        extended = pd.concat([extended, new_row])

    future_df = pd.DataFrame(future_data, index=future_dates)

    # Enrich with external features (same as training data)
    if station_id is not None:
        future_df['quantity'] = 0  # placeholder needed by enrich_features
        future_df = enrich_features(future_df, station_id, fuel_type_id)
        future_df = future_df.drop(columns=['quantity'], errors='ignore')

    return future_df