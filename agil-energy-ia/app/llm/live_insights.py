"""
Live MySQL insights — queries real-time state (current alerts, top stations,
stock-risk stations, recent predictions) and produces indexable documents.

These are refreshed independently of the static index so they can be updated
frequently without re-embedding the whole knowledge base.
"""
import logging
from datetime import datetime, timedelta
from app.services.database import get_connection

logger = logging.getLogger(__name__)


def _safe_query(query: str, params: tuple = ()) -> list[dict]:
    """Execute a SQL query and return rows as list of dicts. Never raises."""
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(query, params)
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        return rows
    except Exception as e:
        logger.error(f"[LiveInsights] Query failed: {e}")
        return []


# =========================================================
# INSIGHT BUILDERS
# =========================================================

def insight_active_alerts() -> list[dict]:
    """Summarize currently active alerts in the system."""
    rows = _safe_query("""
        SELECT a.severity, a.alert_type, a.message, s.name AS station_name, a.created_at
        FROM alerts a
        LEFT JOIN stations s ON a.station_id = s.id
        WHERE a.status = 'ACTIVE'
        ORDER BY FIELD(a.severity, 'HIGH', 'MEDIUM', 'LOW'), a.created_at DESC
        LIMIT 15
    """)
    if not rows:
        return [{
            "text": "État actuel des alertes AGIL : aucune alerte active au moment de la mise à jour "
                    "de la base de connaissances. Le réseau est en fonctionnement normal.",
            "source": "live_data",
            "metadata": {"category": "alerts_status", "active_count": 0},
        }]

    high = [r for r in rows if r["severity"] == "HIGH"]
    medium = [r for r in rows if r["severity"] == "MEDIUM"]
    low = [r for r in rows if r["severity"] == "LOW"]

    text = (
        f"État actuel des alertes AGIL : {len(rows)} alertes actives au moment de la mise à jour "
        f"(HIGH : {len(high)}, MEDIUM : {len(medium)}, LOW : {len(low)}). "
    )
    # Show the 5 most critical
    sample = (high + medium + low)[:5]
    lines = [
        f"- [{r['severity']}] {r['alert_type']} — {r['station_name'] or 'station inconnue'} : {r['message']}"
        for r in sample
    ]
    text += "Alertes prioritaires :\n" + "\n".join(lines)

    return [{
        "text": text,
        "source": "live_data",
        "metadata": {
            "category": "alerts_status",
            "active_count": len(rows),
            "high_count": len(high),
        },
    }]


def insight_top_stations_recent() -> list[dict]:
    """Top 5 stations by sales volume in the last 30 days."""
    rows = _safe_query("""
        SELECT s.id AS station_id, st.name AS station_name, st.region,
               SUM(s.quantity) AS total_qty, COUNT(DISTINCT s.sale_date) AS n_days
        FROM sales s
        JOIN stations st ON s.station_id = st.id
        WHERE s.validated = TRUE
          AND s.sale_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        GROUP BY s.station_id, st.name, st.region
        ORDER BY total_qty DESC
        LIMIT 5
    """)
    if not rows:
        return []

    lines = [
        f"{i}. {r['station_name']} ({r['region']}) : {float(r['total_qty']):,.0f} L sur {r['n_days']} jours"
        for i, r in enumerate(rows, 1)
    ]
    return [{
        "text": (
            "Top 5 des stations AGIL par volume vendu sur les 30 derniers jours :\n"
            + "\n".join(f"- {l}" for l in lines)
        ),
        "source": "live_data",
        "metadata": {"category": "top_stations_30d"},
    }]


def insight_stock_risk_stations() -> list[dict]:
    """Stations with tanks below 20% of capacity (rupture risk)."""
    rows = _safe_query("""
        SELECT s.name AS station_name, st.region, ft.name AS fuel_type,
               t.current_stock, t.capacity,
               (t.current_stock / t.capacity) * 100 AS pct_remaining
        FROM tanks t
        JOIN stations s ON t.station_id = s.id
        LEFT JOIN stations st ON s.id = st.id
        JOIN fuel_types ft ON t.fuel_type_id = ft.id
        WHERE t.capacity > 0 AND (t.current_stock / t.capacity) < 0.20
        ORDER BY pct_remaining ASC
        LIMIT 10
    """)
    if not rows:
        return [{
            "text": "État actuel des stocks AGIL : aucune station n'est actuellement sous le seuil "
                    "critique de 20% de remplissage. Les niveaux de stock sont globalement sains.",
            "source": "live_data",
            "metadata": {"category": "stock_risk", "at_risk_count": 0},
        }]

    lines = [
        f"- {r['station_name']} ({r.get('region', 'N/A')}) : "
        f"{r['fuel_type']} à {float(r['pct_remaining']):.1f}% de capacité "
        f"({float(r['current_stock']):,.0f} L / {float(r['capacity']):,.0f} L)"
        for r in rows
    ]
    return [{
        "text": (
            f"État actuel des stocks à risque AGIL : {len(rows)} station(s)/carburant(s) sous le "
            f"seuil critique de 20% de remplissage :\n"
            + "\n".join(lines)
        ),
        "source": "live_data",
        "metadata": {"category": "stock_risk", "at_risk_count": len(rows)},
    }]


def insight_recent_predictions() -> list[dict]:
    """Summarize recently generated predictions."""
    rows = _safe_query("""
        SELECT s.name AS station_name, ft.name AS fuel_type,
               AVG(p.predicted_quantity) AS avg_pred,
               MIN(p.prediction_date) AS from_date,
               MAX(p.prediction_date) AS to_date,
               p.model_name
        FROM predictions p
        JOIN stations s ON p.station_id = s.id
        JOIN fuel_types ft ON p.fuel_type_id = ft.id
        WHERE p.prediction_date >= CURDATE()
        GROUP BY s.name, ft.name, p.model_name
        ORDER BY s.name, ft.name
        LIMIT 10
    """)
    if not rows:
        return []

    sample = rows[:5]
    lines = [
        f"- {r['station_name']} / {r['fuel_type']} : moyenne prévue {float(r['avg_pred']):,.0f} L/jour "
        f"(modèle : {r['model_name']})"
        for r in sample
    ]
    return [{
        "text": (
            f"État actuel des prévisions AGIL : {len(rows)} combinaisons station/carburant avec "
            f"prévisions actives. Extrait :\n"
            + "\n".join(lines)
        ),
        "source": "live_data",
        "metadata": {"category": "predictions_status", "count": len(rows)},
    }]


def insight_recent_closures() -> list[dict]:
    """Closures active or completed in the last 14 days."""
    rows = _safe_query("""
        SELECT COUNT(*) AS cnt
        FROM sales
        WHERE sale_date >= DATE_SUB(CURDATE(), INTERVAL 14 DAY)
    """)
    # This is a proxy if you have a closures table; otherwise we can use the CSV
    # For now, report on prediction volume
    return []


# =========================================================
# MAIN ENTRY
# =========================================================

def compute_live_insights() -> list[dict]:
    """Build all live-data insight documents. Called at index build time."""
    logger.info("[LiveInsights] Querying live MySQL state...")
    insights = []
    insights.extend(insight_active_alerts())
    insights.extend(insight_top_stations_recent())
    insights.extend(insight_stock_risk_stations())
    insights.extend(insight_recent_predictions())

    # Add a timestamp marker
    insights.append({
        "text": f"Les informations 'live_data' ci-dessus reflètent l'état du système AGIL "
                f"au moment de la dernière mise à jour de la base de connaissances "
                f"({datetime.now().strftime('%Y-%m-%d %H:%M')}).",
        "source": "live_data",
        "metadata": {"category": "timestamp", "updated_at": datetime.now().isoformat()},
    })

    logger.info(f"[LiveInsights] Built {len(insights)} live insights.")
    return insights