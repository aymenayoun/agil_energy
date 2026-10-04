"""
RAG Service — retrieves relevant context (CSVs, prediction history,
feature-importance explanations) and asks the LLM to answer.

Strategy: build an in-memory FAISS index at startup from your CSV files
(holidays, Ramadan, fuel-price history, weather patterns) plus live DB rows.
Rebuild daily (or on demand).
"""
import os
import logging
import pickle
from typing import Optional
import numpy as np
import pandas as pd
import faiss

from app.llm.model_router import get_router
from app.config.settings import MODEL_DIR
from app.llm.hybrid_retriever import HybridRetriever
from app.llm.data_insights import compute_all_data_insights
from app.llm.live_insights import compute_live_insights

logger = logging.getLogger(__name__)

# Store index under saved_models/rag_index.pkl
INDEX_PATH = os.path.join(MODEL_DIR, "rag_index.pkl")
DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "data")


class RAGService:
    def __init__(self):
        self.router = get_router()
        self.hybrid: Optional[HybridRetriever] = None
        self.documents: list[dict] = []  # [{text, source, metadata}]
        self.dim = 768  # nomic-embed-text

    # ---------- Index building ----------
    def build_index(self):
        """Build FAISS index from CSV data + static knowledge."""
        logger.info("[RAG] Building index...")
        self.documents = []

        # 1. Holidays
        df = self._safe_read("01_jours_feries_tunisie.csv")
        for _, row in df.iterrows():
            self.documents.append({
                "text": f"Jour férié en Tunisie le {row.get('date')} : {row.get('nom_ferie', '')}. "
                        f"Type : {row.get('type', 'national')}.",
                "source": "holidays",
                "metadata": {"date": str(row.get("date"))},
            })

        # 2. Fuel prices
        df = self._safe_read("02_prix_carburant.csv")
        for _, row in df.iterrows():
            self.documents.append({
                "text": f"Prix du carburant {row.get('fuel_type')} a changé le "
                        f"{row.get('date_changement')} : de {row.get('ancien_prix_tnd')} TND à "
                        f"{row.get('nouveau_prix_tnd')} TND.",
                "source": "fuel_prices",
                "metadata": {"fuel": str(row.get("fuel_type"))},
            })

        # 3. Religious events — consolidated per event type (avoid duplicates across years)
        df = self._safe_read("04_ramadan_fetes_religieuses.csv")
        if not df.empty:
            by_event: dict[str, list] = {}
            for _, row in df.iterrows():
                by_event.setdefault(row["evenement"], []).append(row)
            for event_name, rows in by_event.items():
                dates_str = ", ".join(
                    f"{r['date_debut']} au {r['date_fin']}" for r in rows
                )
                self.documents.append({
                    "text": (
                        f"Événement religieux : {event_name}. Dates historiques connues : {dates_str}. "
                        f"Impact typique sur la consommation de carburant : augmentation avant le début "
                        f"de la période (effet 'rush' d'approvisionnement), baisse pendant l'événement."
                    ),
                    "source": "religious_events",
                    "metadata": {"event": event_name, "n_occurrences": len(rows)},
                })
        # 4. Station geography
        df = self._safe_read("05_geographie_stations.csv")
        for _, row in df.iterrows():
            self.documents.append({
                "text": f"Station {row.get('station_id')} située en zone "
                        f"{row.get('type_zone')}, gouvernorat {row.get('gouvernorat', '')}.",
                "source": "geography",
                "metadata": {"station_id": int(row.get("station_id", 0))},
            })
            
        # 4.5 School vacations — consolidated per type (qualitative — numbers come from data_insights)
        df = self._safe_read("03_calendrier_scolaire.csv")
        vacation_context = {
            "hiver": (
                "Les vacances d'hiver (fin décembre à début janvier) coïncident avec les fêtes "
                "de fin d'année et le Nouvel An. L'effet net sur la consommation de carburant est "
                "généralement modéré : les déplacements familiaux vers l'intérieur du pays "
                "(Ain Draham, Tabarka, Kairouan) et le tourisme local sont partiellement compensés "
                "par les conditions hivernales (froid, pluie) qui réduisent les trajets non-essentiels."
            ),
            "ete": (
                "Les vacances d'été (juin à septembre) sont la période la plus longue de l'année. "
                "Elles sont marquées par un fort tourisme interne, des déplacements vers les côtes "
                "(Hammamet, Sousse, Monastir, Djerba), et le retour des Tunisiens résidents à "
                "l'étranger (TRE)."
            ),
            "printemps": (
                "Les vacances de printemps (mars) sont associées à des excursions scolaires et "
                "des visites familiales. Impact visible surtout sur les axes touristiques."
            ),
            "automne": (
                "Les vacances d'automne (fin octobre) sont les plus courtes. Quelques déplacements "
                "familiaux courts vers l'intérieur du pays."
            ),
        }

        # Group by type, consolidate dates across school years
        by_type: dict[str, list] = {}
        for _, row in df.iterrows():
            nom = str(row.get("nom_vacances", "")).lower()
            vtype = next((k for k in vacation_context if k in nom), "other")
            by_type.setdefault(vtype, []).append(row)

        for vtype, rows in by_type.items():
            if vtype == "other":
                continue
            dates_str = "; ".join(
                f"{r['date_debut']} au {r['date_fin']} (année scolaire {r['annee_scolaire']})"
                for r in rows
            )
            extra = vacation_context.get(vtype, "")
            self.documents.append({
                "text": (
                    f"Vacances scolaires en Tunisie — {vtype.capitalize()}. "
                    f"Dates historiques connues : {dates_str}. {extra}"
                ),
                "source": "school_vacations",
                "metadata": {"type": vtype, "n_occurrences": len(rows)},
            })
        # 5. Static domain knowledge (qualitative only — numbers come from data_insights)
        self.documents.extend([
            {
                "text": "Pendant le Ramadan, la consommation de carburant tend à diminuer "
                        "pendant la journée (jeûne), avec un pic après l'Iftar (rupture du jeûne). "
                        "L'effet net dépend de la balance entre baisse diurne et hausse nocturne.",
                "source": "domain_knowledge", "metadata": {}
            },
            {
                "text": "Les jours précédant l'Aïd el-Fitr et l'Aïd el-Adha sont marqués par "
                        "des déplacements familiaux importants (retour au village, visites) et "
                        "génèrent un effet 'rush' de réapprovisionnement en carburant.",
                "source": "domain_knowledge", "metadata": {}
            },
            {
                "text": "Une anomalie de type HIGH (score > 0.8) indique souvent : vol, fuite de "
                        "cuve, erreur de saisie, ou panne non déclarée. Le niveau MEDIUM peut "
                        "simplement refléter un événement exceptionnel (grève, fête locale).",
                "source": "domain_knowledge", "metadata": {}
            },
            {
                "text": "Une prédiction de rupture (days_before_rupture <= 3) déclenche une alerte "
                        "de priorité HIGH si <= 1 jour, MEDIUM sinon. Le gestionnaire doit "
                        "contacter le dépôt pour réapprovisionnement prioritaire.",
                "source": "domain_knowledge", "metadata": {}
            },
            {
                "text": "Les features les plus importantes pour la prédiction sont typiquement : "
                        "lag_1 (ventes J-1), rolling_mean_7 (moyenne 7 derniers jours), "
                        "is_weekend, is_ramadan, fuel_price, et temperature_moy.",
                "source": "domain_knowledge", "metadata": {}
            },
            {
                "text": "Les fermetures pour 'coupure d'électricité' et 'panne de pompe' entraînent "
                        "systématiquement une réduction de capacité (capacite_reduite = oui), "
                        "contrairement à la maintenance planifiée ou aux ruptures de stock qui peuvent "
                        "parfois être gérées sans arrêt complet de service.",
                "source": "domain_knowledge", "metadata": {}
            },
            {
                "text": "Une rupture de stock observée sur une station indique généralement soit un "
                        "retard de livraison, soit une sous-estimation de la demande par le système "
                        "de prévision. C'est un événement à croiser avec les alertes de rupture "
                        "(days_before_rupture) pour valider la qualité des prédictions.",
                "source": "domain_knowledge", "metadata": {}
            },
            {
                "text": "Les campagnes promotionnelles AGIL ciblent principalement trois moments : "
                        "(1) le Ramadan avec des offres de services annexes, (2) l'été pour capter le "
                        "tourisme interne, et (3) les fêtes de fin d'année avec des bonus de fidélité. "
                        "Ces périodes se chevauchent avec les pics naturels de consommation.",
                "source": "domain_knowledge", "metadata": {}
            },
            {
                "text": "Les stations AGIL se répartissent en deux profils principaux : (1) les grandes "
                        "stations 24h/24 avec 8 pompes ou plus, situées sur les axes majeurs et offrant "
                        "plusieurs services annexes (lavage, boutique, café) ; (2) les stations plus "
                        "petites à ouverture partielle (06h-22h) avec 3-4 pompes, typiquement en zones "
                        "urbaines denses ou rurales.",
                "source": "domain_knowledge", "metadata": {}
            },
            {
                "text": "Le nombre de pompes et la présence de services annexes sont des facteurs "
                        "structurels qui influencent le volume de ventes d'une station : plus de pompes "
                        "= capacité de traitement plus élevée, services annexes = attractivité accrue "
                        "et fidélisation des clients.",
                "source": "domain_knowledge", "metadata": {}
            },
        ])
        # 7. Station closures / maintenance — consolidated per reason
        df = self._safe_read("06_pannes_fermetures.csv")
        if not df.empty:
            reason_labels = {
                "maintenance": "maintenance planifiée",
                "panne_pompe": "panne de pompe",
                "coupure_electricite": "coupure d'électricité",
                "rupture_stock": "rupture de stock",
                "travaux": "travaux d'infrastructure",
            }
            # One consolidated doc per reason, summarizing frequency
            by_reason: dict[str, list] = {}
            for _, row in df.iterrows():
                by_reason.setdefault(str(row.get("raison", "autre")), []).append(row)

            for reason, rows in by_reason.items():
                n = len(rows)
                stations_affected = sorted({int(r["station_id"]) for r in rows})
                capacity_reduced = sum(1 for r in rows if str(r.get("capacite_reduite", "non")).lower() == "oui")
                label = reason_labels.get(reason, reason)
                self.documents.append({
                    "text": (
                        f"Fermetures de stations AGIL pour cause de {label} : {n} incidents historiques "
                        f"recensés, touchant {len(stations_affected)} stations différentes. "
                        f"{capacity_reduced} de ces incidents ont entraîné une réduction de capacité. "
                        f"Cette catégorie d'incident est l'une des causes récurrentes d'interruption "
                        f"de service dans le réseau AGIL."
                    ),
                    "source": "closures",
                    "metadata": {"reason": reason, "n_incidents": n, "n_stations": len(stations_affected)},
                })

        # 8. Promotions — one doc per campaign
        df = self._safe_read("07_promotions.csv")
        if not df.empty:
            for _, row in df.iterrows():
                stations = str(row.get("stations_concernees", "toutes"))
                scope = "tout le réseau AGIL" if stations.lower() == "toutes" else f"stations {stations}"
                self.documents.append({
                    "text": (
                        f"Campagne promotionnelle AGIL : « {row.get('description', '')} » (type : "
                        f"{row.get('type_promo', '')}). Active du {row.get('date_debut')} au "
                        f"{row.get('date_fin')}, applicable sur {scope}. Les promotions de ce type "
                        f"sont généralement associées à une hausse ponctuelle de la demande."
                    ),
                    "source": "promotions",
                    "metadata": {
                        "type": str(row.get("type_promo", "")),
                        "scope": stations,
                        "start": str(row.get("date_debut")),
                        "end": str(row.get("date_fin")),
                    },
                })

        # 9. Station infrastructure — one doc per station
        df = self._safe_read("08_infrastructure_stations.csv")
        if not df.empty:
            for _, row in df.iterrows():
                services = str(row.get("services_annexes", "")).replace(",", ", ")
                horaires = str(row.get("horaires", ""))
                is_24h = "24h/24" in horaires
                total_pompes = int(row.get("nb_pompes_gasoil", 0)) + int(row.get("nb_pompes_essence", 0)) + int(row.get("nb_pompes_gpl", 0))
                self.documents.append({
                    "text": (
                        f"Station AGIL n°{int(row.get('station_id'))} : "
                        f"{int(row.get('nb_pompes_gasoil', 0))} pompes GASOIL, "
                        f"{int(row.get('nb_pompes_essence', 0))} pompes essence, "
                        f"{int(row.get('nb_pompes_gpl', 0))} pompes GPL "
                        f"(total {total_pompes} pompes). Horaires : {horaires}"
                        f"{' (ouverture 24h/24)' if is_24h else ' (ouverture partielle)'}. "
                        f"Effectif : {int(row.get('nb_employes', 0))} employés. "
                        f"Superficie : {int(row.get('superficie_m2', 0))} m². "
                        f"Services annexes : {services or 'aucun'}."
                    ),
                    "source": "infrastructure",
                    "metadata": {
                        "station_id": int(row.get("station_id", 0)),
                        "total_pumps": total_pompes,
                        "is_24h": is_24h,
                    },
                })

        # 6. Data-driven insights (computed from MySQL sales history)
        try:
            data_insights = compute_all_data_insights()
            self.documents.extend(data_insights)
            logger.info(f"[RAG] Added {len(data_insights)} data-driven insights.")
        except Exception as e:
            logger.error(f"[RAG] Failed to compute data insights: {e}")

        if not self.documents:
            logger.warning("[RAG] No documents to index.")
            return
        
        # 10. Live MySQL insights (current alerts, stock-risk, top stations)
        try:
            live_docs = compute_live_insights()
            self.documents.extend(live_docs)
            logger.info(f"[RAG] Added {len(live_docs)} live-data insights.")
        except Exception as e:
            logger.error(f"[RAG] Failed to compute live insights: {e}")
        
        
        # Embed all documents
        logger.info(f"[RAG] Embedding {len(self.documents)} documents...")
        texts = [d["text"] for d in self.documents]
        embeddings = self.router.embed(texts)
        embeddings = np.array(embeddings, dtype="float32")

        self.dim = embeddings.shape[1]
        self.hybrid = HybridRetriever(self.documents, embeddings.copy())

        # Persist (save un-normalized embeddings so reload reconstructs properly)
        os.makedirs(MODEL_DIR, exist_ok=True)
        with open(INDEX_PATH, "wb") as f:
            pickle.dump({"documents": self.documents, "embeddings": embeddings}, f)

        logger.info(f"[RAG] Hybrid index built: {len(self.documents)} docs, dim={self.dim}")

        # Normalize for cosine similarity (IndexFlatIP + normalized = cosine)
        faiss.normalize_L2(embeddings)
        self.dim = embeddings.shape[1]

        self.index = faiss.IndexFlatIP(self.dim)
        self.index.add(embeddings)

        # Persist
        os.makedirs(MODEL_DIR, exist_ok=True)
        with open(INDEX_PATH, "wb") as f:
            pickle.dump({"documents": self.documents, "embeddings": embeddings}, f)

        logger.info(f"[RAG] Index built: {self.index.ntotal} vectors, dim={self.dim}")

    def load_index(self) -> bool:
            """Load index from disk if available."""
            if not os.path.exists(INDEX_PATH):
                return False
            try:
                with open(INDEX_PATH, "rb") as f:
                    data = pickle.load(f)
                self.documents = data["documents"]
                embeddings = data["embeddings"]
                self.dim = embeddings.shape[1]
                self.hybrid = HybridRetriever(self.documents, embeddings.copy())
                logger.info(f"[RAG] Hybrid index loaded: {len(self.documents)} docs")
                return True
            except Exception as e:
                logger.error(f"[RAG] Failed to load index: {e}")
                return False

    def _safe_read(self, filename: str) -> pd.DataFrame:
        try:
            return pd.read_csv(os.path.join(DATA_DIR, filename))
        except Exception as e:
            logger.warning(f"[RAG] Could not read {filename}: {e}")
            return pd.DataFrame()

    # ---------- Retrieval ----------
    def retrieve(self, query: str, k: int = 5) -> list[dict]:
            if self.hybrid is None:
                return []
            q_emb = np.array(self.router.embed([query])[0], dtype="float32")
            return self.hybrid.retrieve(query, q_emb, k=k)

    # ---------- High-level: answer ----------
    def answer(self, question: str, mode: str = "default", extra_context: str = "") -> dict:
        # Retrieve a wider candidate pool so we can reassemble a balanced context
        raw_hits = self.retrieve(question, k=15)

        if not raw_hits:
            context = ""
        else:
            # Split hits by source type
            insights = [h for h in raw_hits if h["source"] in ("data_insights", "live_data")]
            qualitative = [h for h in raw_hits if h["source"] not in ("data_insights", "live_data")]
            
            # Always take up to 4 data_insights within 0.15 of the BEST insight score
            kept_insights = []
            if insights:
                best_insight_score = insights[0]["score"]
                for h in insights:
                    if best_insight_score - h["score"] <= 0.15 and len(kept_insights) < 4:
                        kept_insights.append(h)

            # Take up to 2 qualitative docs that match the top-category
            kept_qualitative = []
            if qualitative:
                # Prefer qualitative docs matching the top-result category
                top_source = raw_hits[0]["source"]
                same_cat = [h for h in qualitative if h["source"] == top_source]
                other_cat = [h for h in qualitative if h["source"] != top_source]

                for h in (same_cat + other_cat)[:2]:
                    kept_qualitative.append(h)

            # Final hits: insights first (so the model sees numbers early),
            # qualitative after (context/why)
            hits = kept_insights + kept_qualitative

            # Fallback if pruning emptied it
            if not hits:
                hits = raw_hits[:5]

        context = "\n".join(f"[{h['source']}] {h['text']}" for h in hits)
        if extra_context:
            context = f"{extra_context}\n\n{context}"

        system = (
            "Tu es un assistant expert du système AGIL Energy (prévision de demande en carburant en Tunisie). "
            "Tu réponds en français, de manière concise, factuelle et utile. "
            "\n\nRÈGLES STRICTES : "
            "\n1. Réponds UNIQUEMENT à la question posée. Ignore le contexte qui n'est pas directement "
            "pertinent, même s'il est fourni. "
            "\n2. Hiérarchie des sources : (a) 'live_data' = état actuel du système (alertes, stocks, "
            "prévisions en cours) — utilise ces infos pour les questions commençant par 'maintenant', "
            "'actuellement', 'aujourd'hui', 'en ce moment' ; (b) 'data_insights' = mesures calculées "
            "sur les données historiques — utilise pour les questions sur les tendances et impacts ; "
            "(c) autres sources = contexte explicatif. "
            "\n3. N'utilise JAMAIS des chiffres ou statistiques d'une catégorie pour répondre sur une "
            "autre catégorie. Exemple : si on demande l'impact des vacances de printemps, n'utilise pas "
            "les chiffres des vacances d'été ou de l'Aid. "
            "\n4. Les sources qualitatives (school_vacations, religious_events, domain_knowledge) "
            "fournissent du contexte causal (POURQUOI) — ne les utilise pas pour chiffrer. "
            "\n5. Si les sources qualitatives contredisent les données mesurées, fais confiance aux "
            "données et ignore l'attente qualitative. "
            "\n6. Ne combine JAMAIS plusieurs pourcentages en une fourchette s'ils concernent des "
            "périodes différentes. "
            "\n7. Ne refuse pas de répondre si le sujet concerne le carburant, l'énergie, les "
            "déplacements, le climat ou le calendrier tunisien. "
            "\n8. Ne mentionne PAS les autres périodes (été si on parle d'hiver, etc.) sauf si la "
            "question demande explicitement une comparaison. "
            "\n9. Ne termine JAMAIS une réponse en introduisant un sujet qui n'a pas été demandé. "
            "Interdit : 'La consommation diminue pendant le Ramadan...' si la question porte sur les "
            "vacances. Interdit : 'Les features importantes sont lag_1, rolling_mean_7...' si la "
            "question porte sur les jours fériés. Reste sur le sujet exact de la question du début "
            "à la fin. "
            "\n10. DÉFINITIONS STRICTES : 'jours fériés' = fêtes nationales ponctuelles (source 'holidays' "
            "ou insight 'holidays'). 'vacances scolaires' = périodes scolaires prolongées (source "
            "'school_vacations'). Ne confonds PAS les deux. "
            "\n11. N'invente JAMAIS de causes qui ne sont pas dans le contexte fourni. Si tu ne connais "
            "pas la raison précise d'un phénomène, dis simplement que la cause n'est pas spécifiée. "
            "Ne suppose PAS des causes météorologiques, climatiques, économiques ou démographiques "
            "si elles ne sont pas mentionnées dans le contexte."
        )
        prompt = f"Contexte:\n{context}\n\nQuestion: {question}\n\nRéponse concise (ne répète pas la question):"
        llm_out = self.router.generate(prompt, system=system, mode=mode, temperature=0.1)

        return {
            "question": question,
            "answer": llm_out.get("answer") if "answer" in llm_out else llm_out,
            "mode": mode,
            "model_used": llm_out.get("model") if "model" in llm_out else "compare",
            "retrieved_context": hits,
        }

    # ---------- High-level: explain a prediction ----------
    def explain_prediction(self, prediction_result: dict, mode: str = "default") -> str:
        """
        Generate a natural-language explanation of a prediction result.
        Uses retrieved context + explicit numerical summary of the prediction.
        """
        station_id = prediction_result.get("station_id")
        fuel_id = prediction_result.get("fuel_type_id")
        forecast = prediction_result.get("forecast_7_days", [])
        best_model = prediction_result.get("best_model", "?")
        anomaly = prediction_result.get("anomaly", {})
        rupture = prediction_result.get("rupture", {})
        fi = prediction_result.get("feature_importance", []) or []
        fi = fi[:5]

        forecast_str = ", ".join(f"{q:.0f}L" for q in forecast) if forecast else "N/A"
        avg_forecast = sum(forecast) / len(forecast) if forecast else 0

        summary = f"""Résultat de prédiction à expliquer (station {station_id}, carburant {fuel_id}):
- Prévision 7 jours : [{forecast_str}] (moyenne : {avg_forecast:.0f} L/jour)
- Meilleur modèle : {best_model}
- Anomalie : score={anomaly.get('anomaly_score', 0)}, niveau={anomaly.get('risk_level', 'LOW')}
- Rupture : {rupture.get('days_before_rupture', 'non imminente')} jours avant rupture estimée
- Top features (importance) : {', '.join(f"{f['feature']}={f['importance']:.3f}" for f in fi) if fi else 'non disponibles'}"""

        question = (
            f"En 4-6 phrases à un gestionnaire de station : "
            f"(1) résume ce que dit la prévision pour les 7 prochains jours ; "
            f"(2) évalue le niveau de risque (anomalie + rupture) ; "
            f"(3) explique les facteurs clés en t'appuyant sur les features les plus importantes "
            f"ET le contexte tunisien pertinent (vacances, Ramadan, fériés, météo) ; "
            f"(4) recommande une action concrète (surveillance, réapprovisionnement, vérification). "
            f"Utilise les chiffres exacts de la prévision. Ne mentionne PAS d'autres stations."
        )
        res = self.answer(question, mode=mode, extra_context=summary)
        return res["answer"]


# Singleton
_rag_instance: Optional[RAGService] = None

def get_rag() -> RAGService:
    global _rag_instance
    if _rag_instance is None:
        _rag_instance = RAGService()
        if not _rag_instance.load_index():
            _rag_instance.build_index()
    return _rag_instance