import { Component, OnInit, OnDestroy, ViewChild, ElementRef, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PredictionService } from '../../../core/services/prediction.service';
import { NotificationService } from '../../../core/services/notification.service';
import Chart from 'chart.js/auto';

interface RegionInfo {
  region: string;
  station_count: number;
  fuel_types: string;
}

interface FuelTypeInfo {
  fuel_type_id: number;
  fuel_type_name: string;
}

@Component({
  selector: 'app-region-prediction-view',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './region-prediction-view.component.html',
  styleUrls: ['./region-prediction-view.component.scss']
})
export class RegionPredictionViewComponent implements OnInit, OnDestroy {

  @ViewChild('regionChart') regionChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('featureChart') featureChartRef!: ElementRef<HTMLCanvasElement>;

  private predictionService = inject(PredictionService);
  private notif = inject(NotificationService);
  private cdr = inject(ChangeDetectorRef);
  regions: RegionInfo[] = [];
  fuelTypes: FuelTypeInfo[] = [];

  selectedRegion: string | null = null;
  selectedFuelTypeId: number | null = null;

  loading = false;
  generating = false;
  iaResult: any = null;
  viewMode: 'simple' | 'expert' = 'simple';

  private chart: Chart | null = null;
  private featureImportanceChart: Chart | null = null;

  ngOnInit(): void {
    this.loadRegions();
  }

  ngOnDestroy(): void {
    if (this.chart) this.chart.destroy();
    if (this.featureImportanceChart) this.featureImportanceChart.destroy();
  }

  loadRegions(): void {
    this.predictionService.getRegions().subscribe({
      next: (res) => {
        if (res.success) this.regions = res.data;
      },
      error: () => this.notif.error('Impossible de charger les régions')
    });
  }

  onRegionChange(): void {
    this.fuelTypes = [];
    this.selectedFuelTypeId = null;
    this.iaResult = null;
    if (this.chart) { this.chart.destroy(); this.chart = null; }
    if (this.featureImportanceChart) { this.featureImportanceChart.destroy(); this.featureImportanceChart = null; }

    if (!this.selectedRegion) return;

    this.predictionService.getRegionFuelTypes(this.selectedRegion).subscribe({
      next: (res) => {
        if (res.success) this.fuelTypes = res.data;
      },
      error: () => this.notif.error('Impossible de charger les carburants pour cette région')
    });
  }

  generate(): void {
    if (!this.selectedRegion || !this.selectedFuelTypeId) return;
    this.generating = true;
    this.iaResult = null;

    this.predictionService.generateRegionPrediction(this.selectedRegion, this.selectedFuelTypeId).subscribe({
      next: (res) => {
        this.generating = false;
        if (res.success && res.data) {
          this.iaResult = res.data;
          setTimeout(() => {
            this.buildChart();
            this.buildFeatureImportanceChart();
          }, 200);
        }
      },
      error: (err) => {
        this.generating = false;
        this.notif.error(err.error?.message || 'Erreur lors de la génération régionale');
      }
    });
  }

  buildChart(): void {
    if (!this.regionChartRef || !this.iaResult?.forecast_7_days?.length) return;

    const forecast = this.iaResult.forecast_7_days;
    const today = new Date();
    const labels = forecast.map((_: number, i: number) => {
      const d = new Date(today);
      d.setDate(d.getDate() + i + 1);
      return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
    });

    if (this.chart) this.chart.destroy();

    Chart.defaults.color = '#A0A0A0';
    Chart.defaults.borderColor = '#2A2A2A';

    this.chart = new Chart(this.regionChartRef.nativeElement, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Demande prévue — Région (L)',
          data: forecast,
          borderColor: '#2D9CDB',
          backgroundColor: 'rgba(45, 156, 219, 0.08)',
          fill: true,
          tension: 0.3,
          pointRadius: 5,
          pointBackgroundColor: '#2D9CDB',
          pointBorderColor: '#1E1E1E',
          pointBorderWidth: 2,
          borderWidth: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
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
            title: { display: true, text: 'Litres (total région)', color: '#666' },
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

  buildFeatureImportanceChart(): void {
    if (!this.featureChartRef || !this.iaResult?.feature_importance?.length) return;

    const features = this.iaResult.feature_importance.slice(0, 12);
    const labels = features.map((f: any) => this.formatFeatureName(f.feature));
    const values = features.map((f: any) => f.importance);

    const colors = values.map((_: number, i: number) => {
      const opacity = 1 - (i * 0.06);
      return i === 0 ? '#2D9CDB' :
             i < 3 ? `rgba(45, 156, 219, ${opacity})` :
             i < 6 ? `rgba(242, 201, 76, ${opacity})` :
                     `rgba(160, 160, 160, ${opacity})`;
    });

    if (this.featureImportanceChart) this.featureImportanceChart.destroy();

    this.featureImportanceChart = new Chart(this.featureChartRef.nativeElement, {
      type: 'bar',
      data: {
        labels,
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
            ticks: { color: '#666', callback: (val: any) => `${(val * 100).toFixed(0)}%` }
          },
          y: {
            grid: { display: false },
            ticks: { color: '#CCC', font: { size: 12 } }
          }
        }
      }
    });
  }

  getModelEntries(): any[] {
    if (!this.iaResult?.models) return [];
    return Object.entries(this.iaResult.models).map(([name, metrics]: [string, any]) => ({
      name, mae: metrics.mae, rmse: metrics.rmse, mape: metrics.mape
    }));
  }

  getRiskBadge(risk: string): string {
    switch (risk) { case 'HIGH': return 'danger'; case 'MEDIUM': return 'warning'; default: return 'success'; }
  }

  getSelectedRegionInfo(): RegionInfo | undefined {
    return this.regions.find(r => r.region === this.selectedRegion);
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
    switch (this.iaResult?.risk_level) {
      case 'HIGH':   return 'Demande régionale élevée attendue. Anticipez les réapprovisionnements.';
      case 'MEDIUM': return 'Demande à surveiller sur la région dans les prochains jours.';
      case 'LOW':    return 'Demande régionale stable. Aucune action particulière requise.';
      default:       return 'Générez une prévision pour obtenir une recommandation.';
    }
  }

  getRegionTomorrow(): number | null {
    const f = this.iaResult?.forecast_7_days;
    return f?.length ? f[0] : null;
  }

  getRegionWeekTotal(): number | null {
    const f = this.iaResult?.forecast_7_days;
    if (!f?.length) return null;
    return f.reduce((sum: number, v: number) => sum + (v || 0), 0);
  }

  onViewModeChange(): void {
    if (!this.iaResult) return;
    this.cdr.detectChanges();
    if (this.iaResult?.forecast_7_days?.length) this.buildChart();
    if (this.viewMode === 'expert' && this.iaResult?.feature_importance?.length) {
      this.buildFeatureImportanceChart();
    }
  }
}
