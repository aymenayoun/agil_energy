"""
Data-driven insights for the RAG knowledge base.

Queries the real sales history to compute actual percentage impacts of:
- School vacations (per type)
- Religious events (Ramadan, Aid)
- National holidays
- Weekend vs weekday
- Per fuel type

These replace hardcoded estimates with measured values from company data.
"""
import logging
import os
import pandas as pd
import numpy as np
from datetime import date, datetime, timedelta

from app.services.database import get_connection

logger = logging.getLogger(__name__)

DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "data")


# ==========================================================
# DATA LOADING
# ==========================================================

def _load_all_sales() -> pd.DataFrame:
    """Load ALL validated sales across all stations/fuels, aggregated by date."""
    conn = get_connection()
    query = """
        SELECT sale_date AS date, SUM(quantity) AS total_qty, COUNT(DISTINCT station_id) AS n_stations
        FROM sales
        WHERE validated = TRUE
        GROUP BY sale_date
        ORDER BY sale_date ASC
    """
    df = pd.read_sql(query, conn)
    conn.close()
    if df.empty:
        return df
    df["date"] = pd.to_datetime(df["date"])
    df = df.set_index("date").asfreq("D")
    df["total_qty"] = df["total_qty"].interpolate(method="linear").bfill().ffill()
    return df


def _load_sales_by_fuel() -> pd.DataFrame:
    """Load sales grouped by date AND fuel type."""
    conn = get_connection()
    query = """
        SELECT s.sale_date AS date, ft.name AS fuel_name, s.fuel_type_id,
               SUM(s.quantity) AS total_qty
        FROM sales s
        JOIN fuel_types ft ON s.fuel_type_id = ft.id
        WHERE s.validated = TRUE
        GROUP BY s.sale_date, ft.name, s.fuel_type_id
        ORDER BY s.sale_date ASC
    """
    df = pd.read_sql(query, conn)
    conn.close()
    if df.empty:
        return df
    df["date"] = pd.to_datetime(df["date"])
    return df


def _load_sales_by_region() -> pd.DataFrame:
    """Load sales grouped by date AND region."""
    conn = get_connection()
    query = """
        SELECT s.sale_date AS date, st.region AS region, SUM(s.quantity) AS total_qty
        FROM sales s
        JOIN stations st ON s.station_id = st.id
        WHERE s.validated = TRUE
        GROUP BY s.sale_date, st.region
        ORDER BY s.sale_date ASC
    """
    df = pd.read_sql(query, conn)
    conn.close()
    if df.empty:
        return df
    df["date"] = pd.to_datetime(df["date"])
    return df


# ==========================================================
# PERIOD MASKS
# ==========================================================

def _load_period_ranges() -> dict:
    """Load all period date ranges from CSVs."""
    periods = {"vacations": [], "religious": [], "holidays": []}

    # School vacations
    vac_path = os.path.join(DATA_DIR, "03_calendrier_scolaire.csv")
    if os.path.exists(vac_path):
        df = pd.read_csv(vac_path, parse_dates=["date_debut", "date_fin"])
        for _, r in df.iterrows():
            nom = str(r["nom_vacances"]).lower()
            vtype = next((k for k in ["hiver", "ete", "printemps", "automne"] if k in nom), "other")
            periods["vacations"].append({
                "type": vtype,
                "start": r["date_debut"].date(),
                "end": r["date_fin"].date(),
            })

    # Religious events
    rel_path = os.path.join(DATA_DIR, "04_ramadan_fetes_religieuses.csv")
    if os.path.exists(rel_path):
        df = pd.read_csv(rel_path, parse_dates=["date_debut", "date_fin"])
        for _, r in df.iterrows():
            periods["religious"].append({
                "name": r["evenement"],
                "start": r["date_debut"].date(),
                "end": r["date_fin"].date(),
            })

    # National holidays
    hol_path = os.path.join(DATA_DIR, "01_jours_feries_tunisie.csv")
    if os.path.exists(hol_path):
        df = pd.read_csv(hol_path, parse_dates=["date"])
        for _, r in df.iterrows():
            periods["holidays"].append({
                "name": r["nom_ferie"],
                "date": r["date"].date(),
            })

    return periods


def _mask_dates(index: pd.DatetimeIndex, date_list: list[tuple[date, date]]) -> np.ndarray:
    """Build a boolean mask: True where index falls in any (start, end) range."""
    mask = np.zeros(len(index), dtype=bool)
    idx_dates = index.date
    for start, end in date_list:
        mask |= (idx_dates >= start) & (idx_dates <= end)
    return mask


def _pct_delta(period_avg: float, baseline_avg: float) -> float:
    """Percent change: (period - baseline) / baseline * 100."""
    if baseline_avg <= 0:
        return 0.0
    return (period_avg - baseline_avg) / baseline_avg * 100


# ==========================================================
# INSIGHT COMPUTATION
# ==========================================================

def compute_vacation_impact(sales: pd.DataFrame, periods: dict) -> list[dict]:
    """
    Compute measured % impact of each vacation type vs. a season-matched baseline.
    The baseline for each vacation type uses only non-vacation days that fall
    in the SAME calendar months as that vacation type — removing seasonal bias.
    """
    insights = []
    if sales.empty:
        return insights

    # Group vacation date-ranges by type
    vac_groups: dict[str, list[tuple[date, date]]] = {}
    for v in periods["vacations"]:
        vac_groups.setdefault(v["type"], []).append((v["start"], v["end"]))

    # Typical months per vacation type (for season-matched baseline)
    season_months = {
        "hiver": {12, 1},
        "ete": {6, 7, 8, 9},
        "printemps": {3, 4},
        "automne": {10, 11},
    }

    all_vac_mask_by_type = {
        vtype: _mask_dates(sales.index, ranges) for vtype, ranges in vac_groups.items()
    }

    for vtype, ranges in vac_groups.items():
        period_mask = all_vac_mask_by_type[vtype]
        if period_mask.sum() < 3:
            continue

        # Season-matched baseline: same months, but excluding THIS vacation type's dates
        months_for_type = season_months.get(vtype, set())
        if months_for_type:
            month_mask = sales.index.month.isin(months_for_type)
            baseline_mask = month_mask & ~period_mask
        else:
            # Fallback: use all non-vacation days
            baseline_mask = ~period_mask

        if baseline_mask.sum() < 5:
            # Not enough same-season non-vacation days — fall back to global
            baseline_mask = ~period_mask

        period_avg = float(sales.loc[period_mask, "total_qty"].mean())
        baseline_avg = float(sales.loc[baseline_mask, "total_qty"].mean())
        delta = _pct_delta(period_avg, baseline_avg)
        n_days = int(period_mask.sum())
        n_baseline_days = int(baseline_mask.sum())

        label_fr = {
            "hiver": "vacances d'hiver",
            "ete": "vacances d'été",
            "printemps": "vacances de printemps",
            "automne": "vacances d'automne",
        }.get(vtype, f"vacances ({vtype})")

        sign = "hausse" if delta >= 0 else "baisse"
        insights.append({
            "text": (
                f"D'après les données historiques AGIL ({n_days} jours de vacances comparés "
                f"à {n_baseline_days} jours hors-vacances de la même saison), "
                f"les {label_fr} entraînent en moyenne une {sign} de "
                f"{abs(delta):.1f}% de la consommation quotidienne de carburant "
                f"par rapport à une journée normale de la même période de l'année "
                f"(moyenne période : {period_avg:,.0f} L/jour ; "
                f"moyenne baseline saisonnière : {baseline_avg:,.0f} L/jour)."
            ),
            "source": "data_insights",
            "metadata": {
                "category": "vacations",
                "type": vtype,
                "n_days": n_days,
                "delta_pct": round(delta, 2),
                "period_avg": round(period_avg, 2),
                "baseline_avg": round(baseline_avg, 2),
                "baseline_type": "season_matched",
            },
        })

    return insights


def compute_religious_impact(sales: pd.DataFrame, periods: dict) -> list[dict]:
    """Compute measured % impact of Ramadan + Aid events vs. season-matched baseline."""
    insights = []
    if sales.empty:
        return insights

    rel_groups: dict[str, list[tuple[date, date]]] = {}
    for e in periods["religious"]:
        rel_groups.setdefault(e["name"], []).append((e["start"], e["end"]))

    for name, ranges in rel_groups.items():
        period_mask = _mask_dates(sales.index, ranges)
        if period_mask.sum() < 2:
            continue

        # Season-matched baseline: same months as the event(s), excluding event days
        months_of_event = set()
        for s, e in ranges:
            cur = s
            while cur <= e:
                months_of_event.add(cur.month)
                cur = cur + timedelta(days=1)

        month_mask = sales.index.month.isin(months_of_event)
        baseline_mask = month_mask & ~period_mask
        if baseline_mask.sum() < 5:
            baseline_mask = ~period_mask

        period_avg = float(sales.loc[period_mask, "total_qty"].mean())
        baseline_avg = float(sales.loc[baseline_mask, "total_qty"].mean())
        delta = _pct_delta(period_avg, baseline_avg)
        n_days = int(period_mask.sum())
        sign = "hausse" if delta >= 0 else "baisse"

        # 3-day pre-event rush for Aid
        pre_delta_txt = ""
        if "Aid" in name or "Fitr" in name or "Adha" in name:
            pre_ranges = [(s - timedelta(days=3), s - timedelta(days=1)) for s, _ in ranges]
            pre_mask = _mask_dates(sales.index, pre_ranges)
            if pre_mask.sum() >= 2:
                pre_avg = float(sales.loc[pre_mask, "total_qty"].mean())
                pre_delta = _pct_delta(pre_avg, baseline_avg)
                pre_sign = "hausse" if pre_delta >= 0 else "baisse"
                pre_delta_txt = (
                    f" Les 3 jours précédant {name} montrent une {pre_sign} mesurée de "
                    f"{abs(pre_delta):.1f}% (effet 'rush' de réapprovisionnement)."
                )

        insights.append({
            "text": (
                f"D'après les données historiques AGIL ({n_days} jours analysés), "
                f"la période {name} entraîne en moyenne une {sign} de "
                f"{abs(delta):.1f}% de la consommation quotidienne de carburant "
                f"par rapport à une journée normale de la même saison "
                f"(moyenne période : {period_avg:,.0f} L/jour ; "
                f"baseline saisonnière : {baseline_avg:,.0f} L/jour).{pre_delta_txt}"
            ),
            "source": "data_insights",
            "metadata": {
                "category": "religious",
                "event": name,
                "n_days": n_days,
                "delta_pct": round(delta, 2),
                "baseline_type": "season_matched",
            },
        })

    return insights


def compute_holiday_impact(sales: pd.DataFrame, periods: dict) -> list[dict]:
    """Aggregate impact of national holidays (single days)."""
    insights = []
    if sales.empty or not periods["holidays"]:
        return insights

    holiday_dates = [h["date"] for h in periods["holidays"]]
    mask = pd.Series(sales.index.date).isin(holiday_dates).values
    if mask.sum() < 3:
        return insights

    baseline_mask = ~mask
    period_avg = float(sales.loc[mask, "total_qty"].mean())
    baseline_avg = float(sales.loc[baseline_mask, "total_qty"].mean())
    delta = _pct_delta(period_avg, baseline_avg)
    n_days = int(mask.sum())
    sign = "hausse" if delta >= 0 else "baisse"

    insights.append({
        "text": (
            f"D'après les données historiques AGIL ({n_days} jours fériés analysés), "
            f"les jours fériés nationaux tunisiens entraînent en moyenne une {sign} "
            f"de {abs(delta):.1f}% de la consommation quotidienne de carburant "
            f"par rapport à un jour ouvré normal."
        ),
        "source": "data_insights",
        "metadata": {
            "category": "holidays",
            "n_days": n_days,
            "delta_pct": round(delta, 2),
        },
    })

    return insights


def compute_weekend_impact(sales: pd.DataFrame) -> list[dict]:
    """Weekend vs. weekday consumption delta."""
    insights = []
    if sales.empty:
        return insights

    weekend_mask = sales.index.dayofweek >= 5
    if weekend_mask.sum() < 10:
        return insights

    weekend_avg = float(sales.loc[weekend_mask, "total_qty"].mean())
    weekday_avg = float(sales.loc[~weekend_mask, "total_qty"].mean())
    delta = _pct_delta(weekend_avg, weekday_avg)
    sign = "hausse" if delta >= 0 else "baisse"

    insights.append({
        "text": (
            f"D'après les données historiques AGIL, les week-ends (samedi-dimanche) "
            f"affichent en moyenne une {sign} de {abs(delta):.1f}% de la consommation "
            f"quotidienne de carburant par rapport aux jours ouvrés "
            f"(moyenne week-end : {weekend_avg:,.0f} L/jour ; "
            f"moyenne semaine : {weekday_avg:,.0f} L/jour)."
        ),
        "source": "data_insights",
        "metadata": {"category": "weekend", "delta_pct": round(delta, 2)},
    })

    return insights


def compute_fuel_ranking(sales_by_fuel: pd.DataFrame) -> list[dict]:
    """Rank fuels by total volume sold."""
    insights = []
    if sales_by_fuel.empty:
        return insights

    totals = (
        sales_by_fuel.groupby("fuel_name")["total_qty"]
        .sum()
        .sort_values(ascending=False)
    )
    total_all = float(totals.sum())
    if total_all <= 0:
        return insights

    ranked_lines = []
    for i, (fuel, qty) in enumerate(totals.items(), 1):
        pct = (qty / total_all) * 100
        ranked_lines.append(f"{i}. {fuel} : {pct:.1f}% du volume total")

    insights.append({
        "text": (
            "D'après les données historiques AGIL, le classement des carburants "
            "par volume total vendu est :\n"
            + "\n".join(ranked_lines)
            + f"\n(Analyse basée sur un volume cumulé de {total_all:,.0f} litres.)"
        ),
        "source": "data_insights",
        "metadata": {"category": "fuel_ranking", "n_fuels": len(totals)},
    })

    return insights


def compute_region_ranking(sales_by_region: pd.DataFrame) -> list[dict]:
    """Rank regions by total volume sold."""
    insights = []
    if sales_by_region.empty:
        return insights

    totals = (
        sales_by_region.groupby("region")["total_qty"]
        .sum()
        .sort_values(ascending=False)
    )
    total_all = float(totals.sum())
    if total_all <= 0:
        return insights

    top5 = totals.head(5)
    lines = [
        f"{region} : {(qty / total_all) * 100:.1f}% du volume total national"
        for region, qty in top5.items()
    ]

    insights.append({
        "text": (
            "D'après les données historiques AGIL, les 5 gouvernorats "
            "avec la plus forte consommation de carburant sont :\n"
            + "\n".join(lines)
        ),
        "source": "data_insights",
        "metadata": {"category": "region_ranking"},
    })

    return insights

def compute_closure_insights() -> list[dict]:
    """Compute aggregate closure statistics from the CSV."""
    insights = []
    path = os.path.join(DATA_DIR, "06_pannes_fermetures.csv")
    if not os.path.exists(path):
        return insights
    try:
        df = pd.read_csv(path, parse_dates=["date_debut", "date_fin"])
    except Exception as e:
        logger.warning(f"[DataInsights] Could not read closures: {e}")
        return insights
    if df.empty:
        return insights

    # Total incidents per reason
    by_reason = df.groupby("raison").size().sort_values(ascending=False)
    lines = [f"{reason} : {count} incidents" for reason, count in by_reason.items()]
    insights.append({
        "text": (
            "D'après les données historiques AGIL, le classement des causes de fermeture "
            "de stations par fréquence est :\n"
            + "\n".join(f"- {l}" for l in lines)
            + f"\n(Total : {len(df)} incidents sur la période analysée, "
            f"touchant {df['station_id'].nunique()} stations différentes.)"
        ),
        "source": "data_insights",
        "metadata": {"category": "closures", "total": int(len(df))},
    })

    # Duration stats
    df["duration_days"] = (df["date_fin"] - df["date_debut"]).dt.days + 1
    avg_dur = float(df["duration_days"].mean())
    max_dur = int(df["duration_days"].max())
    insights.append({
        "text": (
            f"D'après les données historiques AGIL, une fermeture de station dure en moyenne "
            f"{avg_dur:.1f} jours, avec un maximum observé de {max_dur} jours consécutifs. "
            f"La gestion de ces incidents est critique pour limiter les ruptures de service."
        ),
        "source": "data_insights",
        "metadata": {"category": "closures_duration", "avg_days": round(avg_dur, 1)},
    })

    # Capacity-reduction rate
    reduced = df[df["capacite_reduite"].astype(str).str.lower() == "oui"]
    pct_reduced = (len(reduced) / len(df)) * 100 if len(df) > 0 else 0
    insights.append({
        "text": (
            f"D'après les données historiques AGIL, {pct_reduced:.1f}% des incidents de fermeture "
            f"({len(reduced)} sur {len(df)}) ont entraîné une réduction effective de la capacité "
            f"de service, les autres ayant pu être gérés sans arrêt total."
        ),
        "source": "data_insights",
        "metadata": {"category": "closures_capacity_impact", "pct_reduced": round(pct_reduced, 1)},
    })

    return insights


def compute_infrastructure_insights() -> list[dict]:
    """Compute aggregate infrastructure statistics."""
    insights = []
    path = os.path.join(DATA_DIR, "08_infrastructure_stations.csv")
    if not os.path.exists(path):
        return insights
    try:
        df = pd.read_csv(path)
    except Exception as e:
        logger.warning(f"[DataInsights] Could not read infrastructure: {e}")
        return insights
    if df.empty:
        return insights

    n_total = len(df)
    n_24h = int((df["horaires"].astype(str).str.contains("24h")).sum())
    n_partial = n_total - n_24h

    df["total_pompes"] = (
        df["nb_pompes_gasoil"].fillna(0)
        + df["nb_pompes_essence"].fillna(0)
        + df["nb_pompes_gpl"].fillna(0)
    )
    avg_pompes = float(df["total_pompes"].mean())
    avg_employes = float(df["nb_employes"].mean())
    avg_superficie = float(df["superficie_m2"].mean())

    insights.append({
        "text": (
            f"D'après les données d'infrastructure AGIL : le réseau compte {n_total} stations, dont "
            f"{n_24h} ouvertes 24h/24 et {n_partial} à ouverture partielle. En moyenne une station "
            f"dispose de {avg_pompes:.1f} pompes toutes énergies confondues, emploie "
            f"{avg_employes:.1f} personnes, et occupe {avg_superficie:.0f} m²."
        ),
        "source": "data_insights",
        "metadata": {
            "category": "infrastructure_overview",
            "n_stations": n_total,
            "n_24h": n_24h,
        },
    })

    # Services annexes frequency
    all_services = []
    for s in df["services_annexes"].dropna().astype(str):
        all_services.extend([x.strip() for x in s.split(",") if x.strip()])
    from collections import Counter
    svc_counts = Counter(all_services).most_common()
    if svc_counts:
        lines = [f"{svc} : {cnt} stations ({cnt / n_total * 100:.0f}%)" for svc, cnt in svc_counts]
        insights.append({
            "text": (
                "D'après les données d'infrastructure AGIL, la répartition des services annexes "
                "dans le réseau est :\n"
                + "\n".join(f"- {l}" for l in lines)
            ),
            "source": "data_insights",
            "metadata": {"category": "services_distribution"},
        })

    return insights
# ==========================================================
# MAIN ENTRY POINT
# ==========================================================

def compute_all_data_insights() -> list[dict]:
    """Compute all data-driven insights. Called from rag_service.build_index."""
    logger.info("[DataInsights] Computing data-driven insights from MySQL...")
    try:
        sales = _load_all_sales()
        if sales.empty:
            logger.warning("[DataInsights] No sales data found — skipping insights.")
            return []

        periods = _load_period_ranges()
        sales_by_fuel = _load_sales_by_fuel()
        sales_by_region = _load_sales_by_region()

        insights = []
        insights.extend(compute_vacation_impact(sales, periods))
        insights.extend(compute_religious_impact(sales, periods))
        insights.extend(compute_holiday_impact(sales, periods))
        insights.extend(compute_weekend_impact(sales))
        insights.extend(compute_fuel_ranking(sales_by_fuel))
        insights.extend(compute_region_ranking(sales_by_region))
        insights.extend(compute_closure_insights())
        insights.extend(compute_infrastructure_insights())

        logger.info(f"[DataInsights] Computed {len(insights)} insights.")
        return insights

    except Exception as e:
        logger.error(f"[DataInsights] Error computing insights: {e}", exc_info=True)
        return []