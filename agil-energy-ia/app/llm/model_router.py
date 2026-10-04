"""
Model Router — single-model setup with llama3.2:3b via Ollama.
"""
import logging
import ollama
from typing import Optional

logger = logging.getLogger(__name__)


class ModelRouter:
    """Single-model LLM wrapper for Ollama."""

    def __init__(
        self,
        model: str = "llama3.2:3b",
        embed_model: str = "nomic-embed-text",
    ):
        self.model = model
        self.embed_model = embed_model

    def generate(
        self,
        prompt: str,
        system: Optional[str] = None,
        mode: str = "default",  # kept for API compatibility, unused
        temperature: float = 0.3,
    ) -> dict:
        """Generate a response from the production model."""
        return {
            "model": self.model,
            "answer": self._call(prompt, system, temperature),
        }

    def _call(self, prompt: str, system: Optional[str], temperature: float) -> str:
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": prompt})

        try:
            response = ollama.chat(
                model=self.model,
                messages=messages,
                options={"temperature": temperature, "num_ctx": 4096},
            )
            return response["message"]["content"].strip()
        except Exception as e:
            logger.error(f"[ModelRouter] Error calling {self.model}: {e}")
            return f"[Erreur LLM: {e}]"

    def embed(self, texts: list[str]) -> list[list[float]]:
        """Generate embeddings via Ollama."""
        vectors = []
        for t in texts:
            try:
                resp = ollama.embeddings(model=self.embed_model, prompt=t)
                vectors.append(resp["embedding"])
            except Exception as e:
                logger.error(f"[ModelRouter] Embedding error: {e}")
                vectors.append([0.0] * 768)
        return vectors


_router_instance: Optional[ModelRouter] = None


def get_router() -> ModelRouter:
    global _router_instance
    if _router_instance is None:
        _router_instance = ModelRouter()
    return _router_instance