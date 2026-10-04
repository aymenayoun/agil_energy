# ============================================
# AGIL Energy — Python IA Microservice (FastAPI)
# Connects to native Ollama via host.docker.internal
# ============================================

FROM python:3.11-slim

WORKDIR /app

# System deps for numpy, scikit-learn, xgboost compilation
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir --timeout=300 --retries=5 -r requirements.txt


# Copy application code
COPY . .

# Create saved_models directory
RUN mkdir -p /app/saved_models

EXPOSE 5000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "5000"]
