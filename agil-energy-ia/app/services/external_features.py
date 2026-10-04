"""
AGIL Energy — External Features Module
=======================================
Enriches the ML feature set with external contextual data:
- Public holidays (Tunisian calendar)
- Fuel prices (historical changes)
- School vacations
- Ramadan & religious events
- Weather data (temperature, rain, humidity)
- Station geography (zone type)
- Closures / maintenance periods
- Promotions / commercial campaigns

This module is designed to be called from feature_engineering.py
via: data = enrich_features(data, station_id, fuel_type_id)
"""

import os
import logging
import pandas as pd
import numpy as np
from datetime import datetime

logger = logging.getLogger(__name__)

# ── Data directory (relative to the IA microservice root) ──
DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "data")


# =====================================================
# LOADERS — Read CSV files once and cache in memory
# =====================================================

_cache = {}

def _load_csv(filename: str, parse_dates=None) -> pd.DataFrame:
    """Load a CSV from the data/ directory with caching."""
    if filename in _cache:
        return _cache[filename]
    
    filepath = os.path.join(DATA_DIR, filename)
    if not os.path.exists(filepath):
        logger.warning(f"[ExternalFeatures] File not found: {filepath}")
        return pd.DataFrame()
    
    try:
        df = pd.read_csv(filepath, parse_dates=parse_dates)
        _cache[filename] = df
        logger.info(f"[ExternalFeatures] Loaded {filename}: {len(df)} rows")
        return df
    except Exception as e:
        logger.error(f"[ExternalFeatures] Error loading {filename}: {e}")
        return pd.DataFrame()


def _load_holidays() -> set:
    """Return a set of holiday dates."""
    df = _load_csv("01_jours_feries_tunisie.csv", parse_dates=["date"])
    if df.empty:
        return set()
    return set(df["date"].dt.date)


def _load_ramadan_periods() -> list:
    """Return list of (start, end, event) tuples."""
    df = _load_csv("04_ramadan_fetes_religieuses.csv", parse_dates=["date_debut", "date_fin"])
    if df.empty:
        return []
    periods = []
    for _, row in df.iterrows():
        periods.append((row["date_debut"].date(), row["date_fin"].date(), row["evenement"]))
    return periods


def _load_school_vacations() -> list:
    """Return list of (start, end, name) tuples."""
    df = _load_csv("03_calendrier_scolaire.csv", parse_dates=["date_debut", "date_fin"])
    if df.empty:
        return []
    return [(row["date_debut"].date(), row["date_fin"].date(), row["nom_vacances"]) for _, row in df.iterrows()]


def _load_fuel_prices() -> pd.DataFrame:
    """Return fuel price changes DataFrame."""
    return _load_csv("02_prix_carburant.csv", parse_dates=["date_changement"])


def _load_weather() -> pd.DataFrame:
    """Return weather DataFrame."""
    return _load_csv("09_meteo_historique.csv", parse_dates=["date"])


def _load_closures() -> pd.DataFrame:
    """Return closures/maintenance DataFrame."""
    return _load_csv("06_pannes_fermetures.csv", parse_dates=["date_debut", "date_fin"])


def _load_promotions() -> pd.DataFrame:
    """Return promotions DataFrame."""
    return _load_csv("07_promotions.csv", parse_dates=["date_debut", "date_fin"])


def _load_geography() -> pd.DataFrame:
    """Return station geography DataFrame."""
    return _load_csv("05_geographie_stations.csv")


# =====================================================
# FEATURE BUILDERS
# =====================================================

def add_holiday_features(data: pd.DataFrame) -> pd.DataFrame:
    """
    Add binary holiday indicators:
    - is_holiday: 1 if the day is a public holiday
    - is_holiday_eve: 1 if the next day is a holiday (people stock up)
    - is_holiday_after: 1 if the previous day was a holiday
    """
    holidays = _load_holidays()
    if not holidays:
        data["is_holiday"] = 0
        data["is_holiday_eve"] = 0
        data["is_holiday_after"] = 0
        return data
    
    dates = data.index.date if hasattr(data.index, 'date') else pd.to_datetime(data.index).date
    
    data["is_holiday"] = [1 if d in holidays else 0 for d in dates]
    
    from datetime import timedelta
    data["is_holiday_eve"] = [1 if (d + timedelta(days=1)) in holidays else 0 for d in dates]
    data["is_holiday_after"] = [1 if (d - timedelta(days=1)) in holidays else 0 for d in dates]
    
    return data


def add_ramadan_features(data: pd.DataFrame) -> pd.DataFrame:
    """
    Add Ramadan and religious event indicators:
    - is_ramadan: 1 during Ramadan month
    - is_eid: 1 during Eid Al Fitr or Eid Al Adha
    - ramadan_day: day number within Ramadan (0 if not Ramadan)
    - is_pre_eid: 1 for the 3 days before Eid (shopping rush)
    """
    periods = _load_ramadan_periods()
    if not periods:
        data["is_ramadan"] = 0
        data["is_eid"] = 0
        data["ramadan_day"] = 0
        data["is_pre_eid"] = 0
        return data
    
    dates = data.index.date if hasattr(data.index, 'date') else pd.to_datetime(data.index).date
    
    is_ramadan = []
    is_eid = []
    ramadan_day = []
    is_pre_eid = []
    
    from datetime import timedelta
    
    for d in dates:
        ram = 0
        eid = 0
        rday = 0
        pre_eid = 0
        
        for start, end, event in periods:
            if start <= d <= end:
                if "Ramadan" in event:
                    ram = 1
                    rday = (d - start).days + 1
                elif "Aid" in event or "Fitr" in event or "Adha" in event:
                    eid = 1
            
            # Pre-Eid: 3 days before Eid starts
            if ("Fitr" in event or "Adha" in event):
                pre_start = start - timedelta(days=3)
                if pre_start <= d < start:
                    pre_eid = 1
        
        is_ramadan.append(ram)
        is_eid.append(eid)
        ramadan_day.append(rday)
        is_pre_eid.append(pre_eid)
    
    data["is_ramadan"] = is_ramadan
    data["is_eid"] = is_eid
    data["ramadan_day"] = ramadan_day
    data["is_pre_eid"] = is_pre_eid
    
    return data


def add_vacation_features(data: pd.DataFrame) -> pd.DataFrame:
    """
    Add school vacation indicators:
    - is_school_vacation: 1 during any school vacation period
    - is_summer_vacation: 1 specifically during summer vacation
    """
    vacations = _load_school_vacations()
    if not vacations:
        data["is_school_vacation"] = 0
        data["is_summer_vacation"] = 0
        return data
    
    dates = data.index.date if hasattr(data.index, 'date') else pd.to_datetime(data.index).date
    
    is_vac = []
    is_summer = []
    
    for d in dates:
        vac = 0
        summer = 0
        for start, end, name in vacations:
            if start <= d <= end:
                vac = 1
                if "ete" in name.lower():
                    summer = 1
        is_vac.append(vac)
        is_summer.append(summer)
    
    data["is_school_vacation"] = is_vac
    data["is_summer_vacation"] = is_summer
    
    return data


def add_fuel_price_features(data: pd.DataFrame, fuel_type_id: int = None) -> pd.DataFrame:
    """
    Add fuel price information:
    - fuel_price: current price of the fuel type
    - price_changed_recently: 1 if price changed in the last 7 days
    - price_change_pct: percentage change from previous price
    """
    df_prices = _load_fuel_prices()
    if df_prices.empty or fuel_type_id is None:
        data["fuel_price"] = 0
        data["price_changed_recently"] = 0
        data["price_change_pct"] = 0
        return data
    
    # Map fuel_type_id to name
    fuel_map = {
        1: "GASOIL", 2: "GASOIL SANS SOUFRE", 3: "GASOIL SS PREMIUM",
        4: "SUPER SP", 5: "SSP PREMIUM", 6: "GPL CARBURANT",
        7: "LUBRIFIANTS", 8: "GPL"
    }
    fuel_name = fuel_map.get(fuel_type_id)
    if not fuel_name:
        data["fuel_price"] = 0
        data["price_changed_recently"] = 0
        data["price_change_pct"] = 0
        return data
    
    fuel_prices = df_prices[df_prices["fuel_type"] == fuel_name].sort_values("date_changement")
    if fuel_prices.empty:
        data["fuel_price"] = 0
        data["price_changed_recently"] = 0
        data["price_change_pct"] = 0
        return data
    
    dates = data.index.date if hasattr(data.index, 'date') else pd.to_datetime(data.index).date
    
    prices = []
    changed = []
    change_pct = []
    
    from datetime import timedelta
    
    for d in dates:
        # Find the applicable price for this date
        applicable = fuel_prices[fuel_prices["date_changement"].dt.date <= d]
        if applicable.empty:
            prices.append(0)
            changed.append(0)
            change_pct.append(0)
        else:
            last = applicable.iloc[-1]
            prices.append(float(last["nouveau_prix_tnd"]))
            
            # Did price change within last 7 days?
            change_date = last["date_changement"].date()
            days_since = (d - change_date).days
            changed.append(1 if 0 <= days_since <= 7 else 0)
            
            old_p = float(last["ancien_prix_tnd"])
            new_p = float(last["nouveau_prix_tnd"])
            pct = ((new_p - old_p) / old_p * 100) if old_p > 0 else 0
            change_pct.append(round(pct, 2) if 0 <= days_since <= 30 else 0)
    
    data["fuel_price"] = prices
    data["price_changed_recently"] = changed
    data["price_change_pct"] = change_pct
    
    return data


def add_weather_features(data: pd.DataFrame, station_id: int = None) -> pd.DataFrame:
    """
    Add weather features:
    - temperature_moy: daily average temperature
    - precipitation_mm: daily precipitation
    - is_hot_day: 1 if temp > 35°C
    - is_rainy_day: 1 if precipitation > 5mm
    - humidite_pct: humidity percentage
    """
    df_weather = _load_weather()
    if df_weather.empty or station_id is None:
        data["temperature_moy"] = 20
        data["precipitation_mm"] = 0
        data["is_hot_day"] = 0
        data["is_rainy_day"] = 0
        return data
    
    station_weather = df_weather[df_weather["station_id"] == station_id].copy()
    if station_weather.empty:
        data["temperature_moy"] = 20
        data["precipitation_mm"] = 0
        data["is_hot_day"] = 0
        data["is_rainy_day"] = 0
        return data
    
    station_weather = station_weather.set_index("date")
    station_weather.index = pd.to_datetime(station_weather.index)
    
    # Merge on date
    data["temperature_moy"] = data.index.map(
        lambda d: station_weather.loc[d, "temperature_moy"] if d in station_weather.index else 20
    ).astype(float)
    
    data["precipitation_mm"] = data.index.map(
        lambda d: station_weather.loc[d, "precipitation_mm"] if d in station_weather.index else 0
    ).astype(float)
    
    data["is_hot_day"] = (data["temperature_moy"] > 35).astype(int)
    data["is_rainy_day"] = (data["precipitation_mm"] > 5).astype(int)
    
    return data


def add_closure_features(data: pd.DataFrame, station_id: int = None) -> pd.DataFrame:
    """
    Add closure/maintenance indicator:
    - is_closed: 1 if the station was closed/reduced on that day
    """
    df_closures = _load_closures()
    if df_closures.empty or station_id is None:
        data["is_closed"] = 0
        return data
    
    station_closures = df_closures[df_closures["station_id"] == station_id]
    if station_closures.empty:
        data["is_closed"] = 0
        return data
    
    dates = data.index.date if hasattr(data.index, 'date') else pd.to_datetime(data.index).date
    
    is_closed = []
    for d in dates:
        closed = 0
        for _, row in station_closures.iterrows():
            if row["date_debut"].date() <= d <= row["date_fin"].date():
                closed = 1
                break
        is_closed.append(closed)
    
    data["is_closed"] = is_closed
    return data


def add_promotion_features(data: pd.DataFrame, station_id: int = None) -> pd.DataFrame:
    """
    Add promotion indicator:
    - is_promotion: 1 if a promotion is active on that day for this station
    """
    df_promos = _load_promotions()
    if df_promos.empty:
        data["is_promotion"] = 0
        return data
    
    dates = data.index.date if hasattr(data.index, 'date') else pd.to_datetime(data.index).date
    
    is_promo = []
    for d in dates:
        promo = 0
        for _, row in df_promos.iterrows():
            if row["date_debut"].date() <= d <= row["date_fin"].date():
                stations_str = str(row.get("stations_concernees", "toutes"))
                if stations_str == "toutes" or (station_id and str(station_id) in stations_str.split(",")):
                    promo = 1
                    break
        is_promo.append(promo)
    
    data["is_promotion"] = is_promo
    return data


def add_geography_features(data: pd.DataFrame, station_id: int = None) -> pd.DataFrame:
    """
    Add static geography features:
    - zone_urbaine, zone_periurbaine, zone_rurale, zone_touristique (one-hot)
    """
    df_geo = _load_geography()
    if df_geo.empty or station_id is None:
        data["zone_urbaine"] = 0
        data["zone_periurbaine"] = 0
        data["zone_rurale"] = 0
        data["zone_touristique"] = 0
        return data
    
    station_geo = df_geo[df_geo["station_id"] == station_id]
    if station_geo.empty:
        data["zone_urbaine"] = 0
        data["zone_periurbaine"] = 0
        data["zone_rurale"] = 0
        data["zone_touristique"] = 0
        return data
    
    zone = station_geo.iloc[0]["type_zone"]
    data["zone_urbaine"] = 1 if zone == "urbaine" else 0
    data["zone_periurbaine"] = 1 if zone == "periurbaine" else 0
    data["zone_rurale"] = 1 if zone == "rurale" else 0
    data["zone_touristique"] = 1 if zone == "touristique" else 0
    
    return data


# =====================================================
# MAIN ENRICHMENT FUNCTION
# =====================================================

def enrich_features(data: pd.DataFrame, station_id: int = None, fuel_type_id: int = None) -> pd.DataFrame:
    """
    Master function: enriches the feature DataFrame with all external data.
    Call this from feature_engineering.py's create_features().
    
    Args:
        data: DataFrame with DatetimeIndex and 'quantity' column + existing features
        station_id: station ID for station-specific data (weather, closures)
        fuel_type_id: fuel type ID for fuel-specific data (prices)
    
    Returns:
        Enriched DataFrame with ~15 additional features
    """
    logger.info(f"[ExternalFeatures] Enriching features for station={station_id}, fuel={fuel_type_id}")
    
    try:
        data = add_holiday_features(data)
        data = add_ramadan_features(data)
        data = add_vacation_features(data)
        data = add_fuel_price_features(data, fuel_type_id)
        data = add_weather_features(data, station_id)
        data = add_closure_features(data, station_id)
        data = add_promotion_features(data, station_id)
        data = add_geography_features(data, station_id)
    except Exception as e:
        logger.error(f"[ExternalFeatures] Error during enrichment: {e}")
        # Return data as-is if enrichment fails — model still works without external features
    
    # Fill any NaN introduced by the enrichment
    data = data.fillna(0)
    
    added = [c for c in data.columns if c not in ['quantity', 'day_of_week', 'month', 'quarter',
             'is_weekend', 'day_of_month', 'day_sin', 'day_cos', 'month_sin', 'month_cos',
             'lag_1', 'lag_7', 'lag_14', 'rolling_mean_7', 'rolling_mean_14',
             'rolling_std_7', 'rolling_std_14', 'daily_change', 'daily_diff', 'ratio_7_14']]
    logger.info(f"[ExternalFeatures] Added {len(added)} external features: {added}")
    
    return data


# =====================================================
# WEATHER API FETCHER (Open-Meteo — 100% free)
# =====================================================

def fetch_weather_from_api(latitude: float, longitude: float, 
                           start_date: str, end_date: str) -> pd.DataFrame:
    """
    Fetch real historical weather data from Open-Meteo API (free, no API key).
    
    Usage:
        df = fetch_weather_from_api(36.8065, 10.1815, "2024-01-01", "2026-03-31")
        df.to_csv("data/09_meteo_historique_REEL.csv", index=False)
    """
    import urllib.request
    import json
    
    url = (
        f"https://archive-api.open-meteo.com/v1/archive?"
        f"latitude={latitude}&longitude={longitude}"
        f"&start_date={start_date}&end_date={end_date}"
        f"&daily=temperature_2m_mean,temperature_2m_min,temperature_2m_max,"
        f"precipitation_sum,relative_humidity_2m_mean,wind_speed_10m_max,"
        f"sunshine_duration"
        f"&timezone=Africa/Tunis"
    )
    
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            data = json.loads(response.read())
        
        daily = data["daily"]
        df = pd.DataFrame({
            "date": daily["time"],
            "temperature_moy": daily.get("temperature_2m_mean"),
            "temperature_min": daily.get("temperature_2m_min"),
            "temperature_max": daily.get("temperature_2m_max"),
            "precipitation_mm": daily.get("precipitation_sum"),
            "humidite_pct": daily.get("relative_humidity_2m_mean"),
            "vent_kmh": daily.get("wind_speed_10m_max"),
            "ensoleillement_h": [round(s / 3600, 1) if s else 0 for s in (daily.get("sunshine_duration") or [])],
        })
        
        logger.info(f"[Weather API] Fetched {len(df)} days of weather data")
        return df
    
    except Exception as e:
        logger.error(f"[Weather API] Error fetching weather: {e}")
        return pd.DataFrame()
