"""
AGIL Energy — Package d'évaluation re-thématisé (thème noir/jaune AGIL)
=======================================================================
Package AUTONOME : il duplique la logique d'évaluation avec l'identité
visuelle AGIL, SANS modifier le package `app.evaluation` d'origine.

Modules :
    agil_theme          — source unique du thème (couleurs, rcParams)
    model_evaluation    — évaluation complète + tous les graphiques
    evaluate_normal     — évaluation « conditions normales » (filtre les pics)
    validate_on_uci     — validation sur le dataset public UCI

Usage (depuis agil-energy-ia/) :
    python -m app.evaluation_themed.model_evaluation --sample
    python -m app.evaluation_themed.evaluate_normal --station=1 --fuel=1
    python -m app.evaluation_themed.validate_on_uci --output=evaluation_uci_themed
"""

from app.evaluation_themed.agil_theme import apply_agil_theme, AGIL, COLORS

__all__ = ["apply_agil_theme", "AGIL", "COLORS"]
