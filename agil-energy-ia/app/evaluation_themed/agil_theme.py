"""
AGIL Energy — Thème graphique « Black & Yellow » pour les figures d'évaluation
================================================================================
Source unique de vérité pour l'apparence de TOUTES les figures matplotlib
générées par les scripts d'évaluation (model_evaluation, evaluate_normal,
validate_on_uci).

Identité visuelle AGIL :
    • Fond noir profond           #0A0A0A
    • Panneaux / axes anthracite  #161616
    • Accent principal — jaune     #FFD200
    • Accent secondaire — ambre    #F5A623
    • Texte / grilles — gris clair #E8E8E8 / #3A3A3A

Usage :
    from app.evaluation_themed.agil_theme import apply_agil_theme, COLORS
    apply_agil_theme()            # à appeler une fois, avant de tracer

Le module met aussi à jour le COLORS partagé (couleurs par modèle), pensées
pour ressortir sur fond noir tout en restant distinctes.
"""

import matplotlib as mpl
import matplotlib.pyplot as plt
from cycler import cycler

# ── Palette AGIL ──────────────────────────────────────────────────────────
AGIL = {
    "black":      "#0A0A0A",   # fond figure
    "panel":      "#161616",   # fond des axes
    "yellow":     "#FFD200",   # accent principal
    "amber":      "#F5A623",   # accent secondaire
    "white":      "#F5F5F5",   # texte principal
    "text_muted": "#B8B8B8",   # texte atténué
    "grid":       "#3A3A3A",   # grille
    "green":      "#7ED321",   # succès / bon
    "red":        "#FF4D4D",   # danger / erreur
    "blue":       "#4FA3D1",   # support
    "teal":       "#36C2A8",   # support
    "purple":     "#B98AE0",   # support
}

# ── Couleurs par modèle (ressortent sur noir, restent distinctes) ─────────
COLORS = {
    "LinearRegression": "#FFD200",   # jaune AGIL — souvent le meilleur modèle
    "RandomForest":     "#36C2A8",   # teal
    "XGBoost":          "#FF7A45",   # orange vif
    "Prophet":          "#B98AE0",   # violet
    "Ensemble":         "#F5A623",   # ambre
    "XGBoostQuantile":  "#4FA3D1",   # bleu
    # alias pratiques
    "Réel":             "#FFFFFF",
    "Train":            "#FFD200",
    "Validation":       "#FF7A45",
}

# Cycle de couleurs par défaut (séries multiples sans couleur explicite)
_CYCLE = [
    "#FFD200", "#FF7A45", "#36C2A8", "#B98AE0",
    "#4FA3D1", "#F5A623", "#7ED321", "#FF4D4D",
]


def apply_agil_theme():
    """Applique le thème AGIL (noir/jaune) globalement à matplotlib."""
    mpl.rcParams.update({
        # ── Fonds ──
        "figure.facecolor":  AGIL["black"],
        "axes.facecolor":    AGIL["panel"],
        "savefig.facecolor": AGIL["black"],
        "savefig.edgecolor": AGIL["black"],

        # ── Texte ──
        "text.color":        AGIL["white"],
        "axes.labelcolor":   AGIL["white"],
        "axes.titlecolor":   AGIL["yellow"],
        "xtick.color":       AGIL["text_muted"],
        "ytick.color":       AGIL["text_muted"],
        "axes.titlesize":    14,
        "axes.titleweight":  "bold",
        "axes.labelsize":    12,
        "font.size":         12,
        "legend.fontsize":   10,

        # ── Bordures (spines) ──
        "axes.edgecolor":    AGIL["grid"],
        "axes.linewidth":    1.2,

        # ── Grille ──
        "axes.grid":         True,
        "grid.color":        AGIL["grid"],
        "grid.alpha":        0.5,
        "grid.linewidth":    0.8,

        # ── Légende ──
        "legend.facecolor":  AGIL["panel"],
        "legend.edgecolor":  AGIL["grid"],
        "legend.framealpha": 0.9,
        "legend.labelcolor": AGIL["white"],

        # ── Figure ──
        "figure.figsize":    (12, 7),
        "figure.dpi":        150,
        "figure.edgecolor":  AGIL["black"],

        # ── Cycle de couleurs ──
        "axes.prop_cycle":   cycler(color=_CYCLE),

        # ── Divers ──
        "lines.linewidth":   2.0,
        "patch.edgecolor":   AGIL["black"],
    })


def style_table(table, header_facecolor=None, header_textcolor=None,
                cell_facecolor=None, cell_textcolor=None, edge_color=None):
    """
    Stylise un matplotlib.table.Table aux couleurs AGIL.
    À appeler après ax.table(...).
    """
    header_facecolor = header_facecolor or AGIL["yellow"]
    header_textcolor = header_textcolor or AGIL["black"]
    cell_facecolor   = cell_facecolor   or AGIL["panel"]
    cell_textcolor   = cell_textcolor   or AGIL["white"]
    edge_color       = edge_color       or AGIL["grid"]

    for (row, col), cell in table.get_celld().items():
        cell.set_edgecolor(edge_color)
        cell.set_linewidth(1.0)
        if row == 0:
            cell.set_facecolor(header_facecolor)
            cell.set_text_props(color=header_textcolor, fontweight="bold")
        else:
            # léger zébrage pour la lisibilité
            cell.set_facecolor(AGIL["panel"] if row % 2 else "#1E1E1E")
            cell.set_text_props(color=cell_textcolor)


def highlight_color():
    """Couleur de surbrillance pour annoter le « meilleur » (bord jaune)."""
    return AGIL["yellow"]
