import mysql.connector
import pandas as pd
from app.config.settings import DB_CONFIG


def get_connection():
    """Établir une connexion à la base MySQL."""
    return mysql.connector.connect(**DB_CONFIG)


def fetch_sales_data(station_id: int, fuel_type_id: int) -> pd.DataFrame:
    """
    Extraire les ventes historiques validées pour une station et un type de carburant.
    Retourne un DataFrame avec colonnes: date, quantity
    """
    conn = get_connection()
    query = """
        SELECT sale_date AS date, quantity
        FROM sales
        WHERE station_id = %s AND fuel_type_id = %s AND validated = TRUE
        ORDER BY sale_date ASC
    """
    df = pd.read_sql(query, conn, params=(station_id, fuel_type_id))
    conn.close()

    if not df.empty:
        df['date'] = pd.to_datetime(df['date'])
        df = df.set_index('date')
        # Remplir les jours manquants par interpolation linéaire
        df = df.asfreq('D')
        df['quantity'] = df['quantity'].interpolate(method='linear').bfill().ffill()

    return df


def fetch_tank_info(station_id: int, fuel_type_id: int) -> dict:
    """Récupérer les informations du réservoir (stock, seuil critique)."""
    conn = get_connection()
    cursor = conn.cursor(dictionary=True)
    cursor.execute(
        "SELECT current_stock, critical_threshold, capacity FROM tanks WHERE station_id = %s AND fuel_type_id = %s",
        (station_id, fuel_type_id)
    )
    result = cursor.fetchone()
    conn.close()
    return result


def save_predictions(station_id: int, fuel_type_id: int, predictions: list, model_name: str):
    """Enregistrer les prévisions dans la table predictions."""
    conn = get_connection()
    cursor = conn.cursor()

    # Supprimer les anciennes prévisions pour cette station/carburant dans la même plage
    if predictions:
        dates = [p['date'] for p in predictions]
        cursor.execute(
            "DELETE FROM predictions WHERE station_id = %s AND fuel_type_id = %s AND prediction_date BETWEEN %s AND %s",
            (station_id, fuel_type_id, min(dates), max(dates))
        )

    # Insérer les nouvelles prévisions
    insert_query = """
        INSERT INTO predictions (station_id, fuel_type_id, prediction_date, predicted_quantity, model_name, confidence_score)
        VALUES (%s, %s, %s, %s, %s, %s)
    """
    for pred in predictions:
        cursor.execute(insert_query, (
            station_id, fuel_type_id,
            pred['date'], round(pred['quantity'], 2),
            model_name, pred.get('confidence', None)
        ))

    conn.commit()
    conn.close()


def save_alert(station_id: int, alert_type: str, severity: str, message: str):
    """Créer une alerte dans la table alerts."""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO alerts (station_id, alert_type, severity, message, status) VALUES (%s, %s, %s, %s, 'ACTIVE')",
        (station_id, alert_type, severity, message)
    )
    conn.commit()
    conn.close()


def save_model_metrics(model_name: str, model_version: str, station_id: int, fuel_type_id: int,
                       mae: float, rmse: float, mape: float):
    """Enregistrer les métriques de performance du modèle."""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        """INSERT INTO model_metrics (model_name, model_version, station_id, fuel_type_id, mae, rmse, mape)
           VALUES (%s, %s, %s, %s, %s, %s, %s)""",
        (model_name, model_version, station_id, fuel_type_id, round(mae, 4), round(rmse, 4), round(mape, 4))
    )
    conn.commit()
    conn.close()


def fetch_all_active_stations() -> list:
    """Récupérer toutes les stations actives avec leurs carburants."""
    conn = get_connection()
    cursor = conn.cursor(dictionary=True)
    cursor.execute("""
        SELECT s.id AS station_id, s.name AS station_name,
               t.fuel_type_id, ft.name AS fuel_type_name
        FROM stations s
        JOIN tanks t ON s.id = t.station_id
        JOIN fuel_types ft ON t.fuel_type_id = ft.id
        WHERE s.status = 'ACTIVE'
        ORDER BY s.id, t.fuel_type_id
    """)
    results = cursor.fetchall()
    conn.close()
    return results

def fetch_model_metrics(station_id: int = None, fuel_type_id: int = None, limit: int = 50) -> list:
    """Fetch recent model performance metrics, optionally filtered."""
    conn = get_connection()
    cursor = conn.cursor(dictionary=True)

    query = """
        SELECT model_name, model_version, station_id, fuel_type_id,
               mae, rmse, mape, created_at
        FROM model_metrics
        WHERE 1=1
    """
    params = []
    if station_id:
        query += " AND station_id = %s"
        params.append(station_id)
    if fuel_type_id:
        query += " AND fuel_type_id = %s"
        params.append(fuel_type_id)

    query += " ORDER BY created_at DESC LIMIT %s"
    params.append(limit)

    cursor.execute(query, params)
    results = cursor.fetchall()
    conn.close()
    # Convert datetime objects to ISO strings for JSON serialization
    for row in results:
        if row.get('created_at') and hasattr(row['created_at'], 'isoformat'):
            row['created_at'] = row['created_at'].isoformat()
    return results

def fetch_region_sales_data(region: str, fuel_type_id: int) -> pd.DataFrame:
    """
    Aggregate daily sales across ALL stations in a given region (gouvernorat).
    Returns a DataFrame with columns: date, quantity (summed across stations).
    """
    conn = get_connection()
    query = """
        SELECT s.sale_date AS date, SUM(s.quantity) AS quantity
        FROM sales s
        JOIN stations st ON s.station_id = st.id
        WHERE st.region = %s AND s.fuel_type_id = %s AND s.validated = TRUE
        GROUP BY s.sale_date
        ORDER BY s.sale_date ASC
    """
    df = pd.read_sql(query, conn, params=(region, fuel_type_id))
    conn.close()

    if not df.empty:
        df['date'] = pd.to_datetime(df['date'])
        df = df.set_index('date')
        df = df.asfreq('D')
        df['quantity'] = df['quantity'].interpolate(method='linear').bfill().ffill()

    return df


def fetch_all_regions() -> list:
    """Fetch all distinct regions that have active stations."""
    conn = get_connection()
    cursor = conn.cursor(dictionary=True)
    cursor.execute("""
        SELECT DISTINCT st.region,
               COUNT(st.id) AS station_count,
               GROUP_CONCAT(DISTINCT ft.name ORDER BY ft.name SEPARATOR ', ') AS fuel_types
        FROM stations st
        JOIN tanks t ON st.id = t.station_id
        JOIN fuel_types ft ON t.fuel_type_id = ft.id
        WHERE st.status = 'ACTIVE'
        GROUP BY st.region
        ORDER BY st.region
    """)
    results = cursor.fetchall()
    conn.close()
    return results


def fetch_region_fuel_types(region: str) -> list:
    """Fetch all fuel types available in a given region."""
    conn = get_connection()
    cursor = conn.cursor(dictionary=True)
    cursor.execute("""
        SELECT DISTINCT ft.id AS fuel_type_id, ft.name AS fuel_type_name
        FROM stations st
        JOIN tanks t ON st.id = t.station_id
        JOIN fuel_types ft ON t.fuel_type_id = ft.id
        WHERE st.region = %s AND st.status = 'ACTIVE'
        ORDER BY ft.id
    """, (region,))
    results = cursor.fetchall()
    conn.close()
    return results