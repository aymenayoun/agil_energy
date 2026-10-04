import logging
from fastapi import FastAPI, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.params import Depends
from pydantic import BaseModel
from typing import Optional, List

from app.config.settings import API_KEY
from app.services.prediction_service import run_prediction_pipeline, run_region_prediction_pipeline
from app.services.database import fetch_all_active_stations, fetch_sales_data
from app.services.feature_engineering import create_features
from app.models.ml_models import detect_anomalies
from app.services.database import fetch_all_active_stations, fetch_sales_data, fetch_model_metrics, fetch_all_regions, fetch_region_fuel_types
from apscheduler.schedulers.background import BackgroundScheduler
from app.llm.rag_service import get_rag
from datetime import datetime
from app.evaluation.model_evaluation import run_full_evaluation

# Configuration du logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ===== Application FastAPI =====
app = FastAPI(
    title="AGIL Energy - Module IA",
    description="Microservice d'Intelligence Artificielle pour la prévision de la demande en carburant et la détection d'anomalies.",
    version="1.0.0"
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ===== Nightly Batch Scheduler =====
def nightly_batch():
    """Runs every night at midnight to retrain models for all active stations."""
    logger.info("=== Démarrage du batch nightly automatique ===")
    stations = fetch_all_active_stations()
    success, errors = 0, 0
    for station in stations:
        try:
            run_prediction_pipeline(station['station_id'], station['fuel_type_id'], 7)
            success += 1
        except Exception as e:
            logger.error(f"Erreur batch pour station {station['station_id']}: {e}")
            errors += 1
    logger.info(f"=== Batch nightly terminé: {success} succès, {errors} erreurs ===")


scheduler = BackgroundScheduler()
scheduler.add_job(nightly_batch, 'cron', hour=0, minute=0)
scheduler.start()

# ===== Modèles Pydantic =====

class RegionPredictionRequest(BaseModel):
    region: str
    fuel_type_id: int
    forecast_days: Optional[int] = 7


class RegionPredictionResponse(BaseModel):
    region: str
    fuel_type_id: int
    status: str
    forecast_7_days: List[float]
    anomaly_score: float
    risk_level: str
    best_model: Optional[str]
    models: Optional[dict]
    ensemble_weights: Optional[dict]
    message: Optional[str] = None
    feature_importance: Optional[list] = None
class PredictionRequest(BaseModel):
    station_id: int
    fuel_type_id: int
    forecast_days: Optional[int] = 7
    include_quantiles: Optional[bool] = True
    include_shap: Optional[bool] = True

class BatchPredictionRequest(BaseModel):
    forecast_days: Optional[int] = 7

class PredictionResponse(BaseModel):
    station_id: int
    fuel_type_id: int
    status: str
    forecast_7_days: List[float]
    anomaly_score: float
    risk_level: str
    days_before_rupture: Optional[float]
    best_model: Optional[str]
    models: Optional[dict]
    ensemble_weights: Optional[dict]
    message: Optional[str] = None
    feature_importance: Optional[list] = None
    quantiles: Optional[dict] = None
    shap: Optional[dict] = None


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str

class ChatRequest(BaseModel):
    question: str
    mode: Optional[str] = "default"  # "default" | "experimental" | "compare"

class ChatResponse(BaseModel):
    question: str
    answer: str | dict
    mode: str
    model_used: Optional[str] = None
    retrieved_context: Optional[list] = None

class ExplainRequest(BaseModel):
    station_id: int
    fuel_type_id: int
    mode: Optional[str] = "default"

class EvaluationRequest(BaseModel):
    station_id: int = 1
    fuel_type_id: int = 1
    use_sample_data: Optional[bool] = False
# ===== Vérification API Key =====
def verify_api_key(x_api_key: Optional[str] = Header(default=None, alias="X-API-Key")):
    """Vérification obligatoire de la clé API interne."""
    if x_api_key is None or x_api_key != API_KEY:
        raise HTTPException(status_code=403, detail="Clé API invalide")


# ===== Endpoints =====

@app.get("/health", response_model=HealthResponse)
def health_check():
    """Endpoint de vérification de santé du microservice."""
    return HealthResponse(
        status="healthy",
        service="AGIL Energy IA Module",
        version="1.0.0"
    )


@app.post("/ml/predict", response_model=PredictionResponse)
def predict(request: PredictionRequest, _: None = Depends(verify_api_key)):
    """
    Endpoint principal de prédiction.
    Appelé par le backend Spring Boot.
    
    Input: station_id, fuel_type_id, forecast_days (optionnel)
    Output: prévisions, score d'anomalie, niveau de risque, jours avant rupture
    """
    logger.info(f"Requête de prédiction: station={request.station_id}, fuel={request.fuel_type_id}")

    try:
        forecast_days = min(max(request.forecast_days or 7, 1), 14)
        result = run_prediction_pipeline(request.station_id, request.fuel_type_id, forecast_days)

        return PredictionResponse(
            station_id=result["station_id"],
            fuel_type_id=result["fuel_type_id"],
            status=result["status"],
            forecast_7_days=result.get("forecast_7_days", []),
            anomaly_score=result.get("anomaly", {}).get("anomaly_score", 0.0),
            risk_level=result.get("anomaly", {}).get("risk_level", "LOW"),
            days_before_rupture=result.get("rupture", {}).get("days_before_rupture"),
            best_model=result.get("best_model"),
            models=result.get("models"),
            ensemble_weights=result.get("ensemble_weights"),
            feature_importance=result.get("feature_importance"),
            quantiles=result.get("quantiles"),
            shap=result.get("shap"),
            message=result.get("message")
        )

    except Exception as e:
        logger.error(f"Erreur de prédiction: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Erreur lors de la prédiction: {str(e)}")


@app.post("/ml/predict/batch")
def predict_batch(request: BatchPredictionRequest = BatchPredictionRequest(), _: None = Depends(verify_api_key)):
    """
    Prédiction en lot pour toutes les stations actives.
    Simule le traitement batch quotidien.
    """
    logger.info("Lancement des prédictions batch pour toutes les stations")

    stations = fetch_all_active_stations()
    results = []
    errors = []

    for station in stations:
        try:
            result = run_prediction_pipeline(
                station['station_id'],
                station['fuel_type_id'],
                request.forecast_days or 7
            )
            results.append({
                "station_id": station['station_id'],
                "station_name": station['station_name'],
                "fuel_type": station['fuel_type_name'],
                "status": result["status"],
                "best_model": result.get("best_model"),
                "risk_level": result.get("anomaly", {}).get("risk_level", "LOW"),
                "days_before_rupture": result.get("rupture", {}).get("days_before_rupture")
            })
        except Exception as e:
            errors.append({
                "station_id": station['station_id'],
                "fuel_type_id": station['fuel_type_id'],
                "error": str(e)
            })

    return {
        "total_processed": len(results),
        "total_errors": len(errors),
        "results": results,
        "errors": errors
    }


@app.get("/ml/anomalies/{station_id}/{fuel_type_id}")
def check_anomalies(station_id: int, fuel_type_id: int, _: None = Depends(verify_api_key)):
    """Vérification des anomalies pour une station/carburant spécifique."""
    try:
        df_raw = fetch_sales_data(station_id, fuel_type_id)
        if df_raw.empty or len(df_raw) < 14:
            return {"anomaly_score": 0.0, "risk_level": "LOW", "message": "Données insuffisantes"}

        df_features = create_features(df_raw)
        result = detect_anomalies(df_features)
        return result

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/ml/stations")
def list_stations():
    """Lister toutes les stations actives avec leurs carburants."""
    try:
        return fetch_all_active_stations()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/ml/model-metrics")
def get_model_metrics(
    station_id: int = None,
    fuel_type_id: int = None,
    limit: int = 50,
    _: None = Depends(verify_api_key)
):
    """Get recent model performance metrics for all or a specific station/fuel combo."""
    try:
        metrics = fetch_model_metrics(station_id, fuel_type_id, limit)
        return {
            "total": len(metrics),
            "metrics": metrics
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# ===== Region-based Prediction Endpoints =====

@app.get("/ml/regions")
def list_regions():
    """List all regions (gouvernorats) with active stations."""
    try:
        return fetch_all_regions()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/ml/regions/{region}/fuel-types")
def list_region_fuel_types(region: str):
    """List available fuel types for a given region."""
    try:
        return fetch_region_fuel_types(region)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ml/predict/region", response_model=RegionPredictionResponse)
def predict_region(request: RegionPredictionRequest, _: None = Depends(verify_api_key)):
    """
    Prédiction agrégée par région (gouvernorat).
    Agrège les ventes de toutes les stations d'une région,
    puis applique le pipeline ML complet.
    """
    logger.info(f"Requête de prédiction régionale: region={request.region}, fuel={request.fuel_type_id}")

    try:
        forecast_days = min(max(request.forecast_days or 7, 1), 14)
        result = run_region_prediction_pipeline(request.region, request.fuel_type_id, forecast_days)

        return RegionPredictionResponse(
            region=result["region"],
            fuel_type_id=result["fuel_type_id"],
            status=result["status"],
            forecast_7_days=result.get("forecast_7_days", []),
            anomaly_score=result.get("anomaly", {}).get("anomaly_score", 0.0),
            risk_level=result.get("anomaly", {}).get("risk_level", "LOW"),
            best_model=result.get("best_model"),
            models=result.get("models"),
            ensemble_weights=result.get("ensemble_weights"),
            feature_importance=result.get("feature_importance"),
            message=result.get("message")
        )

    except Exception as e:
        logger.error(f"Erreur de prédiction régionale: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Erreur lors de la prédiction régionale: {str(e)}")

# ===== LLM / RAG Endpoints =====

@app.post("/ai/chat", response_model=ChatResponse)
def ai_chat(request: ChatRequest, _: None = Depends(verify_api_key)):
    """Ask a question to the RAG assistant."""
    try:
        rag = get_rag()
        result = rag.answer(request.question, mode=request.mode or "default")
        return ChatResponse(**result)
    except Exception as e:
        logger.error(f"Erreur chat IA: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/explain")
def ai_explain(request: ExplainRequest, _: None = Depends(verify_api_key)):
    """Run a prediction then return a natural-language explanation."""
    try:
        pred = run_prediction_pipeline(request.station_id, request.fuel_type_id, 7)
        rag = get_rag()
        explanation = rag.explain_prediction(pred, mode=request.mode or "default")
        return {
            "station_id": request.station_id,
            "fuel_type_id": request.fuel_type_id,
            "prediction": pred,
            "explanation": explanation,
        }
    except Exception as e:
        logger.error(f"Erreur explain IA: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/rag/rebuild")
def ai_rebuild_index(_: None = Depends(verify_api_key)):
    """Rebuild the RAG index (run after updating CSV data)."""
    try:
        rag = get_rag()
        rag.build_index()
        return {"status": "ok", "vectors": rag.index.ntotal if rag.index else 0}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/ai/rag/refresh-live")
def ai_refresh_live(_: None = Depends(verify_api_key)):
    """
    Rebuild the RAG index with fresh live MySQL data.
    Use this when you want the chatbot to know about current alerts/stocks
    without waiting for the full periodic rebuild.
    """
    try:
        rag = get_rag()
        rag.build_index()
        return {
            "status": "ok",
            "vectors": rag.hybrid.faiss_index.ntotal if rag.hybrid else 0,
            "refreshed_at": datetime.now().isoformat(),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/ml/evaluate")
def evaluate_models(request: EvaluationRequest, _: None = Depends(verify_api_key)):
    """Évaluation complète — génère tous les graphiques pour le rapport."""
    try:
        output_dir = f"evaluation_output/station_{request.station_id}_fuel_{request.fuel_type_id}"
        results = run_full_evaluation(
            station_id=request.station_id,
            fuel_type_id=request.fuel_type_id,
            output_dir=output_dir,
            use_sample_data=request.use_sample_data
        )
        return {
            "status": "success",
            "metrics": results["metrics"],
            "files_generated": results["files"],
            "output_directory": results["output_dir"],
        }
    except Exception as e:
        logger.error(f"Erreur évaluation: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

import atexit
atexit.register(lambda: scheduler.shutdown())
@app.get("/ml/debug/metrics")
def debug_metrics():
    """Temporary debug endpoint — no auth — remove before production."""
    try:
        from app.services.database import fetch_model_metrics
        metrics = fetch_model_metrics(None, None, 10)
        return {"total": len(metrics), "metrics": metrics, "raw_count": len(metrics)}
    except Exception as e:
        return {"error": str(e), "type": type(e).__name__}
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5000)
    
    
#uvicorn app.main:app --host 0.0.0.0 --port 5000 --reload