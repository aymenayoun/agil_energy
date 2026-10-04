import os

# Database configuration
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USER", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": os.getenv("DB_NAME", "agil_energy"),
}

# ML Configuration
ML_CONFIG = {
    "min_data_points": 90,          # Minimum 90 jours de données
    "forecast_days": 7,             # Prévision sur 7 jours par défaut
    "max_forecast_days": 14,        # Maximum 14 jours
    "anomaly_contamination": 0.05,  # 5% d'anomalies attendues
    "retrain_threshold": 0.05,      # Rollback si dégradation > 5%
    "mape_target": 0.15,            # MAPE cible < 15%
}

# Model storage
MODEL_DIR = os.getenv("MODEL_DIR", "saved_models")

# API Security
API_KEY = os.getenv("API_KEY", "")