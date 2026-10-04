import { Component, OnInit, OnDestroy, ViewChild, ElementRef, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PredictionService, ModelMetric } from '../../../core/services/prediction.service';
import { StationService } from '../../../core/services/station.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Prediction, Station } from '../../../core/models';
import Chart from 'chart.js/auto';
import { StationPickerComponent } from '../../../shared/components/station-picker/station-picker.component';
import { AIService } from '../../../core/services/ai.service';
import { ShapExplainerComponent } from '../shap-explainer/shap-explainer.component';

@Component({
  selector: 'app-prediction-view',
  standalone: true,
  imports: [CommonModule, FormsModule, StationPickerComponent,ShapExplainerComponent],
  templateUrl: './prediction-view.component.html',
  styleUrls: ['./prediction-view.component.scss']
})
export class PredictionViewComponent implements OnInit, OnDestroy {

  @ViewChild('predChart') predChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('featureChart') featureChartRef!: ElementRef<HTMLCanvasElement>;

  private predictionService = inject(PredictionService);
  private stationService    = inject(StationService);
  private notif             = inject(NotificationService);
  private featureImportanceChart: Chart | null = null;
  private aiService = inject(AIService);
  private cdr = inject(ChangeDetectorRef);

  aiExplanation: string | null = null;
  aiLoading = false;
  aiError: string | null = null;

  stations: Station[] = [];
  fuelTypes: { id: number; name: string }[] = [];
  predictions: Prediction[] = [];
  metrics: ModelMetric[] = [];

  selectedStationId: number | null = null;
  selectedFuelTypeId: number | null = null;
  metricsStationId: number | null = null;

  activeTab: 'predict' | 'metrics' = 'predict';
  viewMode: 'simple' | 'expert' = 'simple';
  loading = false;
  generating = false;
  metricsLoading = false;
  hasSearched = false;
  iaHealthy = false;
  iaResult: any = null;
  batchLoading = false;
  batchMessage = '';
  batchSuccess = false;

  private chart: Chart | null = null;

  ngOnInit(): void {
    this.stationService.getAllStations().subscribe({
      next: (res) => { if (res.success) this.stations = res.data; },
      error: () => this.notif.error('Impossible de charger les stations')
    });
    this.checkHealth();
  }

  ngOnDestroy(): void {
    if (this.chart) this.chart.destroy();
    if (this.featureImportanceChart) this.featureImportanceChart.destroy();
  }

  checkHealth(): void {
    this.predictionService.checkIAHealth().subscribe({
      next: (res) => { this.iaHealthy = res.success && res.data?.ia_service === 'available'; },
      error: () => { this.iaHealthy = false; }
    });
  }

  onStationChange(): void {
    const station = this.stations.find(s => s.id === this.selectedStationId);
    this.fuelTypes = station?.tanks?.map(t => ({ id: t.fuelTypeId, name: t.fuelTypeName })) ?? [];
    this.selectedFuelTypeId = null;
    this.iaResult = null;
    this.predictions = [];
    this.hasSearched = false;
  }

  generateAndLoad(): void {
    if (!this.selectedStationId || !this.selectedFuelTypeId) return;
    this.generating = true;
    this.iaResult = null;

    this.predictionService.generatePrediction(this.selectedStationId, this.selectedFuelTypeId).subscribe({
      next: (res) => {
        this.generating = false;
        if (res.success && res.data) {
          this.iaResult = res.data;
          setTimeout(() => {
            this.buildFeatureImportanceChart();
          }, 200);
        }
        this.loadPredictions();
      },
      error: (err) => {
        this.generating = false;
        this.batchMessage = err.error?.message || 'Erreur lors de la génération';
        this.batchSuccess = false;
        this.notif.error(this.batchMessage);
      }
    });
  }

  loadPredictions(): void {
    if (!this.selectedStationId || !this.selectedFuelTypeId) return;
    this.loading = true;
    this.hasSearched = true;

    this.predictionService.getPredictions(this.selectedStationId, this.selectedFuelTypeId).subscribe({
      next: (res) => {
        this.predictions = res.success ? res.data : [];
        this.loading = false;
        setTimeout(() => this.buildChart(), 100);
      },
      error: () => {
        this.notif.error('Erreur lors du chargement des prévisions');
        this.loading = false;
      }
    });
  }

  loadMetrics(): void {
    this.metricsLoading = true;
    this.predictionService.getModelMetrics(this.metricsStationId ?? undefined).subscribe({
      next: (res) => {
        console.log('Metrics response:', res);
        console.log('Metrics data:', res.data);
        console.log('Metrics array:', res.data?.metrics);
        this.metrics = res.success ? (res.data?.metrics ?? []) : [];
        console.log('Metrics assigned:', this.metrics.length);
        this.metricsLoading = false;
      },
      error: (err) => {
        console.error('Metrics error:', err);
        this.notif.error('Erreur lors du chargement des métriques');
        this.metricsLoading = false;
      }
    });
  }

  generateBatch(): void {
    this.batchLoading = true;
    this.batchMessage = '';
    this.predictionService.generateBatch().subscribe({
      next: (res) => {
        this.batchLoading = false;
        this.batchSuccess = true;
        const d = res.data;
        this.batchMessage = `Prédictions générées pour ${d?.total_processed ?? 0} station(s). Erreurs: ${d?.total_errors ?? 0}`;
        this.notif.success(this.batchMessage);
      },
      error: (err) => {
        this.batchLoading = false;
        this.batchSuccess = false;
        this.batchMessage = err.error?.message || 'Erreur lors du batch';
        this.notif.error(this.batchMessage);
      }
    });
  }

  buildChart(): void {
    if (!this.predChartRef || this.predictions.length === 0) return;
    const labels = this.predictions.map(p =>
      new Date(p.predictionDate).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' })
    );
    const pointData = this.predictions.map(p => p.predictedQuantity);

    if (this.chart) this.chart.destroy();

    Chart.defaults.color = '#A0A0A0';
    Chart.defaults.borderColor = '#2A2A2A';

    const datasets: any[] = [
      {
        label: 'Prévision (ensemble)',
        data: pointData,
        borderColor: '#F2C94C',
        backgroundColor: 'rgba(242, 201, 76, 0.08)',
        fill: false,
        tension: 0.3,
        pointRadius: 5,
        pointBackgroundColor: '#F2C94C',
        pointBorderColor: '#1E1E1E',
        pointBorderWidth: 2,
        borderWidth: 2,
        order: 1
      }
    ];

    // Add quantile bands if the current iaResult has them AND lengths match
    const q = this.iaResult?.quantiles;
    if (q && q.P10 && q.P90 && q.P10.length >= pointData.length) {
      datasets.push({
        label: 'P90 (scénario pessimiste)',
        data: q.P90.slice(0, pointData.length),
        borderColor: 'rgba(231, 76, 60, 0.6)',
        backgroundColor: 'rgba(231, 76, 60, 0.12)',
        fill: '+1',
        tension: 0.3,
        pointRadius: 0,
        borderWidth: 1,
        borderDash: [4, 4],
        order: 2
      });
      datasets.push({
        label: 'P10 (scénario optimiste)',
        data: q.P10.slice(0, pointData.length),
        borderColor: 'rgba(46, 204, 113, 0.6)',
        backgroundColor: 'rgba(0, 0, 0, 0)',
        fill: false,
        tension: 0.3,
        pointRadius: 0,
        borderWidth: 1,
        borderDash: [4, 4],
        order: 3
      });
    }

    this.chart = new Chart(this.predChartRef.nativeElement, {
      type: 'line',
      data: { labels, datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: {
            display: true,
            position: 'top',
            labels: { color: '#A0A0A0', usePointStyle: true }
          },
          tooltip: {
            backgroundColor: '#1E1E1E',
            titleColor: '#E8E8E8',
            bodyColor: '#A0A0A0',
            borderColor: '#333',
            borderWidth: 1,
            cornerRadius: 8,
            padding: 10
          }
        },
        scales: {
          y: {
            beginAtZero: false,
            title: { display: true, text: 'Litres', color: '#666' },
            grid: { color: '#1E1E1E' },
            ticks: { color: '#666' }
          },
          x: {
            grid: { display: false },
            ticks: { color: '#888' }
          }
        }
      }
    });
  }

  getStationName(id: number): string {
    return this.stations.find(s => s.id === id)?.name ?? `Station #${id}`;
  }

  getRiskBadge(risk: string): string {
    switch (risk) { case 'HIGH': return 'danger'; case 'MEDIUM': return 'warning'; default: return 'success'; }
  }

  getMapeQuality(mape: number): string {
    if (mape < 0.10) return 'success';
    if (mape < 0.20) return 'warning';
    return 'danger';
  }

  getMapeLabel(mape: number): string {
    if (mape < 0.10) return 'Excellent';
    if (mape < 0.20) return 'Acceptable';
    return 'Faible';
  }

  getModelEntries(): any[] {
    if (!this.iaResult?.models) return [];
    return Object.entries(this.iaResult.models).map(([name, metrics]: [string, any]) => ({
      name, mae: metrics.mae, rmse: metrics.rmse, mape: metrics.mape
    }));
  }
  buildFeatureImportanceChart(): void {
    if (!this.featureChartRef || !this.iaResult?.feature_importance?.length) return;

    const features = this.iaResult.feature_importance.slice(0, 12);
    const labels = features.map((f: any) => this.formatFeatureName(f.feature));
    const values = features.map((f: any) => f.importance);

    // Color gradient: top features get accent color, lower ones fade
    const colors = values.map((_: number, i: number) => {
      const opacity = 1 - (i * 0.06);
      return i === 0 ? '#F2C94C' :
             i < 3 ? `rgba(242, 201, 76, ${opacity})` :
             i < 6 ? `rgba(45, 156, 219, ${opacity})` :
                     `rgba(160, 160, 160, ${opacity})`;
    });

    if (this.featureImportanceChart) this.featureImportanceChart.destroy();

    Chart.defaults.color = '#A0A0A0';
    Chart.defaults.borderColor = '#2A2A2A';

    this.featureImportanceChart = new Chart(this.featureChartRef.nativeElement, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [{
          label: 'Importance',
          data: values,
          backgroundColor: colors,
          borderRadius: 4,
          borderSkipped: false
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#1E1E1E',
            titleColor: '#E8E8E8',
            bodyColor: '#A0A0A0',
            borderColor: '#333',
            borderWidth: 1,
            cornerRadius: 8,
            padding: 10,
            callbacks: {
              label: (ctx: any) => `Importance: ${(ctx.parsed.x * 100).toFixed(1)}%`
            }
          }
        },
        scales: {
          x: {
            beginAtZero: true,
            grid: { color: '#1E1E1E' },
            ticks: {
              color: '#666',
              callback: (val: any) => `${(val * 100).toFixed(0)}%`
            }
          },
          y: {
            grid: { display: false },
            ticks: { color: '#CCC', font: { size: 12 } }
          }
        }
      }
    });
  }

  formatFeatureName(name: string): string {
    const nameMap: Record<string, string> = {
      'rolling_mean_7': 'Moyenne 7j',
      'rolling_mean_14': 'Moyenne 14j',
      'rolling_std_7': 'Écart-type 7j',
      'rolling_std_14': 'Écart-type 14j',
      'lag_1': 'Vente J-1',
      'lag_7': 'Vente J-7',
      'lag_14': 'Vente J-14',
      'day_of_week': 'Jour semaine',
      'day_sin': 'Jour (sin)',
      'day_cos': 'Jour (cos)',
      'month': 'Mois',
      'month_sin': 'Mois (sin)',
      'month_cos': 'Mois (cos)',
      'quarter': 'Trimestre',
      'is_weekend': 'Weekend',
      'day_of_month': 'Jour du mois',
      'daily_change': 'Variation jour.',
      'daily_diff': 'Diff. journalière',
      'ratio_7_14': 'Ratio 7j/14j',
      'temperature_moy': 'Température',
      'precipitation_mm': 'Précipitations',
      'is_hot_day': 'Jour chaud',
      'is_rainy_day': 'Jour pluvieux',
      'is_holiday': 'Jour férié',
      'is_holiday_eve': 'Veille férié',
      'is_holiday_after': 'Lendemain férié',
      'is_ramadan': 'Ramadan',
      'ramadan_day': 'Jour Ramadan',
      'is_eid': 'Aïd',
      'is_pre_eid': 'Pré-Aïd',
      'is_school_vacation': 'Vacances scol.',
      'is_summer_vacation': 'Vacances été',
      'fuel_price': 'Prix carburant',
      'price_changed_recently': 'Changement prix',
      'price_change_pct': '% changement prix',
      'is_closed': 'Station fermée',
      'is_promotion': 'Promotion',
      'zone_urbaine': 'Zone urbaine',
      'zone_periurbaine': 'Zone périurbaine',
      'zone_rurale': 'Zone rurale',
      'zone_touristique': 'Zone touristique',
      'humidite_pct': 'Humidité',
    };
    return nameMap[name] || name;
  }

  explainWithAI(): void {
    if (!this.selectedStationId || !this.selectedFuelTypeId) return;
    if (!this.iaResult || this.iaResult.status !== 'success') {
      this.notif.error('Générez d\'abord une prédiction avant de demander une explication');
      return;
    }
    this.aiLoading = true;
    this.aiError = null;
    this.aiExplanation = null;
    this.aiService.explain(this.selectedStationId, this.selectedFuelTypeId, 'default').subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.aiExplanation = res.data.explanation;
        } else {
          this.aiError = 'Réponse inattendue du service IA';
        }
        this.aiLoading = false;
      },
      error: (err) => {
        this.aiError = err?.error?.message || err?.message || 'Erreur inconnue';
        this.aiLoading = false;
      }
    });
  }

  clearAIExplanation(): void {
    this.aiExplanation = null;
    this.aiError = null;
  }
  getQuantileSummary(): string | null {
    const q = this.iaResult?.quantiles;
    if (!q?.metrics) return null;
    const calibrated = q.metrics.coverage_80_calibrated_pct;
    const offset = q.metrics.calibration_offset;
    const raw = q.metrics.coverage_80_pct;
    if (calibrated && calibrated !== raw) {
      return `Couverture 80% : ${calibrated}% (après calibration conformale +${offset} L, brute : ${raw}%)`;
    }
    return `Couverture 80% : ${raw}% · Largeur moyenne : ${q.metrics.interval_width_pct}%`;
  }

  // ===== Simplified view helpers =====
  getSimpleRiskLabel(): string {
    switch (this.iaResult?.risk_level) {
      case 'HIGH':   return 'Risque élevé';
      case 'MEDIUM': return 'Risque modéré';
      case 'LOW':    return 'Situation normale';
      default:       return 'Non évalué';
    }
  }

  getSimpleRiskMessage(): string {
    const d = this.iaResult?.days_before_rupture;
    switch (this.iaResult?.risk_level) {
      case 'HIGH':
        return d
          ? `Réapprovisionnement urgent recommandé. Rupture possible dans ~${Math.round(d)} jour(s).`
          : 'Réapprovisionnement urgent recommandé.';
      case 'MEDIUM':
        return d
          ? `À surveiller. Rupture possible dans ~${Math.round(d)} jour(s).`
          : 'À surveiller dans les prochains jours.';
      case 'LOW':
        return 'Les stocks couvrent la demande prévue. Aucune action immédiate requise.';
      default:
        return 'Générez une prévision pour obtenir une recommandation.';
    }
  }

  getTomorrowForecast(): number | null {
    if (this.predictions?.length) return this.predictions[0].predictedQuantity;
    const p50 = this.iaResult?.quantiles?.P50;
    return p50?.length ? p50[0] : null;
  }

  getWeekTotal(): number | null {
    if (!this.predictions?.length) return null;
    return this.predictions.reduce((sum, p) => sum + (p.predictedQuantity || 0), 0);
  }

  onViewModeChange(): void {
    if (!this.iaResult) return;
    this.cdr.detectChanges();
    if (this.predictions.length > 0) this.buildChart();
    if (this.viewMode === 'expert' && this.iaResult?.feature_importance?.length) {
      this.buildFeatureImportanceChart();
    }
  }
}
