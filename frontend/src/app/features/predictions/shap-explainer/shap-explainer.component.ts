import {
  Component,
  computed,
  input,
  signal,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  ViewChild,
  effect
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import Chart, { ChartConfiguration } from 'chart.js/auto';
import { ShapExplanation, ShapFeature } from '../../../core/services/prediction.service';

/**
 * Visualises SHAP values for a single forecast.
 * - A day selector (Day 1 … Day N) lets the user inspect per-day top features.
 * - A horizontal bar chart shows each feature's contribution (red = pushes
 *   prediction down, green = pushes it up) around the model's base value.
 * - A second, smaller chart shows globally most important features
 *   (mean absolute SHAP across all days).
 */
@Component({
  selector: 'app-shap-explainer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './shap-explainer.component.html',
  styleUrls: ['./shap-explainer.component.scss']
})
export class ShapExplainerComponent implements AfterViewInit, OnDestroy {
  shap     = input.required<ShapExplanation>();
  forecast = input.required<number[]>();

  selectedDay = 0;
  private perDayChart?: Chart;
  private globalChart?: Chart;

  @ViewChild('perDayCanvas', { static: true }) perDayCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('globalCanvas', { static: true }) globalCanvas!: ElementRef<HTMLCanvasElement>;

  days          = computed(() => this.shap().top_features_per_day.map((_, i) => i));
  dayPrediction = computed(() => this.forecast()[this.selectedDay] ?? 0);

  constructor() {
    // Rebuild charts whenever inputs change
    effect(() => {
      const _ = this.shap();
      if (this.perDayChart) { this.rebuildPerDay(); this.rebuildGlobal(); }
    });
  }

  ngAfterViewInit(): void {
    this.rebuildPerDay();
    this.rebuildGlobal();
  }

  ngOnDestroy(): void {
    this.perDayChart?.destroy();
    this.globalChart?.destroy();
  }

  onDayChange(): void { this.rebuildPerDay(); }

  private rebuildPerDay(): void {
    const features = this.shap().top_features_per_day[this.selectedDay] ?? [];
    const sorted   = [...features].sort((a, b) => Math.abs(b.shap_value) - Math.abs(a.shap_value));
    const labels   = sorted.map(f => this.prettifyFeatureName(f.feature));
    const values   = sorted.map(f => f.shap_value);
    // Theme-aware colors: green = pushes prediction up, red = pushes it down
    const colors   = values.map(v => v >= 0 ? 'rgba(39, 174, 96, 0.75)' : 'rgba(235, 87, 87, 0.75)');

    Chart.defaults.color = '#A0A0A0';
    Chart.defaults.borderColor = '#2A2A2A';

    const cfg: ChartConfiguration<'bar'> = {
      type: 'bar',
      data: { labels, datasets: [{ label: 'Contribution (L)', data: values, backgroundColor: colors, borderRadius: 4 }] },
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
              label: ctx => {
                const f       = sorted[ctx.dataIndex];
                const contrib = (f.shap_value >= 0 ? '+' : '') + f.shap_value.toFixed(1) + ' L';
                return `${contrib}   (valeur observée : ${f.value ?? '—'})`;
              }
            }
          }
        },
        scales: {
          x: {
            title: { display: true, text: 'Contribution SHAP (L)', color: '#666' },
            grid:  { color: '#1E1E1E' },
            ticks: { color: '#888' }
          },
          y: {
            grid:  { display: false },
            ticks: { color: '#CCC', font: { size: 12 } }
          }
        }
      }
    };

    this.perDayChart?.destroy();
    this.perDayChart = new Chart(this.perDayCanvas.nativeElement, cfg);
  }

  private rebuildGlobal(): void {
    const top    = this.shap().global_top_features ?? [];
    const sorted = [...top].sort((a, b) => b.mean_abs_shap - a.mean_abs_shap);
    const labels = sorted.map(f => this.prettifyFeatureName(f.feature));
    const values = sorted.map(f => f.mean_abs_shap);

    // Color gradient matching prediction-view's feature importance chart:
    // top features get accent (yellow), middle ones blue, lower ones grey
    const colors = values.map((_, i) => {
      const opacity = 1 - (i * 0.06);
      return i === 0 ? '#F2C94C' :
             i < 3  ? `rgba(242, 201, 76, ${opacity})` :
             i < 6  ? `rgba(45, 156, 219, ${opacity})` :
                      `rgba(160, 160, 160, ${opacity})`;
    });

    Chart.defaults.color = '#A0A0A0';
    Chart.defaults.borderColor = '#2A2A2A';

    const cfg: ChartConfiguration<'bar'> = {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: '|SHAP| moyen',
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
            padding: 10
          }
        },
        scales: {
          x: {
            title: { display: true, text: 'Importance globale (|SHAP| moyen)', color: '#666' },
            grid:  { color: '#1E1E1E' },
            ticks: { color: '#888' }
          },
          y: {
            grid:  { display: false },
            ticks: { color: '#CCC', font: { size: 12 } }
          }
        }
      }
    };

    this.globalChart?.destroy();
    this.globalChart = new Chart(this.globalCanvas.nativeElement, cfg);
  }

  private prettifyFeatureName(f: string): string {
    const map: Record<string, string> = {
      'sales_lag_1':     'Ventes J-1',
      'sales_lag_7':     'Ventes J-7',
      'sales_lag_14':    'Ventes J-14',
      'rolling_mean_7':  'Moyenne 7j',
      'rolling_mean_14': 'Moyenne 14j',
      'rolling_std_7':   'Écart-type 7j',
      'day_of_week':     'Jour de la semaine',
      'is_weekend':      'Week-end',
      'month':           'Mois',
      'day_of_month':    'Jour du mois',
      'is_ramadan':      'Ramadan',
      'is_holiday':      'Jour férié',
      'temperature':     'Température',
    };
    return map[f] ?? f.replace(/_/g, ' ');
  }
}
