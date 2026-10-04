"""
Hybrid retriever: combines FAISS semantic search + BM25 keyword search.
Fixes the weakness of pure embeddings on short templated documents.
"""
import logging
from typing import Optional
import numpy as np
import faiss
from rank_bm25 import BM25Okapi
import re

logger = logging.getLogger(__name__)


def _tokenize(text: str) -> list[str]:
    """Simple tokenizer: lowercase, strip accents loosely, split on non-word chars."""
    text = text.lower()
    # Preserve accented French characters
    tokens = re.findall(r"[a-zà-ÿ0-9]+", text)
    return [t for t in tokens if len(t) > 1]


class HybridRetriever:
    """Combines FAISS (semantic) and BM25 (keyword) with weighted fusion."""

    def __init__(
        self,
        documents: list[dict],
        embeddings: np.ndarray,
        semantic_weight: float = 0.5,
        bm25_weight: float = 0.5,
    ):
        self.documents = documents
        self.semantic_weight = semantic_weight
        self.bm25_weight = bm25_weight

        # Build FAISS index
        self.dim = embeddings.shape[1]
        self.faiss_index = faiss.IndexFlatIP(self.dim)
        faiss.normalize_L2(embeddings)
        self.faiss_index.add(embeddings)

        # Build BM25 index
        tokenized_corpus = [_tokenize(d["text"]) for d in documents]
        self.bm25 = BM25Okapi(tokenized_corpus)
        logger.info(f"[Hybrid] Indexed {len(documents)} docs (semantic + BM25)")

    def retrieve(self, query: str, query_embedding: np.ndarray, k: int = 5, fetch_k: int = 20) -> list[dict]:
        """
        Retrieve top-k with hybrid scoring.
        fetch_k: number of candidates to fetch from each method before re-ranking.
        """
        n = len(self.documents)
        fetch_k = min(fetch_k, n)

        # --- Semantic scores ---
        q_vec = query_embedding.astype("float32").reshape(1, -1)
        faiss.normalize_L2(q_vec)
        sem_scores_raw, sem_idx = self.faiss_index.search(q_vec, n)
        # FAISS returns cosine similarity in [-1, 1]; map to [0, 1]
        sem_scores = np.zeros(n)
        for score, idx in zip(sem_scores_raw[0], sem_idx[0]):
            if idx >= 0:
                sem_scores[idx] = (score + 1) / 2  # normalize to [0,1]

        # --- BM25 scores ---
        tokenized_query = _tokenize(query)
        bm25_scores_raw = self.bm25.get_scores(tokenized_query)
        # Normalize BM25 scores to [0,1]
        if bm25_scores_raw.max() > 0:
            bm25_scores = bm25_scores_raw / bm25_scores_raw.max()
        else:
            bm25_scores = bm25_scores_raw

        # --- Fusion ---
        combined = (
            self.semantic_weight * sem_scores
            + self.bm25_weight * bm25_scores
        )

        # Top-k
        top_idx = np.argsort(combined)[::-1][:k]
        results = []
        for i in top_idx:
            if combined[i] <= 0:
                continue
            doc = self.documents[i].copy()
            doc["score"] = float(combined[i])
            doc["semantic_score"] = float(sem_scores[i])
            doc["bm25_score"] = float(bm25_scores[i])
            results.append(doc)
        return results