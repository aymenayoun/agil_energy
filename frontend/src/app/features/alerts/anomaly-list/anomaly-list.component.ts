import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AlertService, AnomalyStats } from '../../../core/services/alert.service';
import { StationService } from '../../../core/services/station.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Alert, Station } from '../../../core/models';
import { StationPickerComponent } from '../../../shared/components/station-picker/station-picker.component';

export interface ParsedAnomaly {
  alert: Alert;
  direction: 'high' | 'low';
  quantity: number | null;
  mean: number | null;
  rangeLow: number | null;
  rangeHigh: number | null;
  zScore: number | null;
}

type SeverityFilter = 'ALL' | 'HIGH' | 'MEDIUM';

/**
 * Sale-anomaly view. Fetches only SALE_ANOMALY alerts from the backend
 * (server-side filter, proper pagination). Parses the structured Z-score /
 * mean / range fields out of the alert message so they can be rendered as
 * stats rather than raw text. Summary cards at the top read lifetime-over-
 * 7-days stats from /api/alerts/stats/anomalies.
 */
@Component({
  selector: 'app-anomaly-list',
  standalone: true,
  imports: [CommonModule, FormsModule, StationPickerComponent],
  templateUrl: './anomaly-list.component.html',
  styleUrls: ['./anomaly-list.component.scss']
})
export class AnomalyListComponent implements OnInit {
  private alertService   = inject(AlertService);
  private stationService = inject(StationService);
  private notif          = inject(NotificationService);

  // --- state ---------------------------------------------------------------
  readonly stations  = signal<Station[]>([]);
  readonly anomalies = signal<ParsedAnomaly[]>([]);
  readonly loading   = signal(true);
  readonly stats     = signal<AnomalyStats | null>(null);

  // --- filters -------------------------------------------------------------
  selectedStationId: number | null = null;
  severityFilter: SeverityFilter = 'ALL';

  // --- pagination ----------------------------------------------------------
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  readonly pageSize = 20;

  // --- derived -------------------------------------------------------------
  readonly filtered = computed(() =>
    this.anomalies().filter(a =>
      this.severityFilter === 'ALL' || a.alert.severity === this.severityFilter
    )
  );

  ngOnInit(): void {
    this.stationService.getAllStations().subscribe({
      next: res => { if (res.success) this.stations.set(res.data); },
      error: () => this.notif.error('Impossible de charger la liste des stations')
    });
    this.loadAnomalies();
    this.loadStats();
  }

  onStationChange(): void {
    this.currentPage = 0;
    this.loadAnomalies();
  }

  loadAnomalies(): void {
    this.loading.set(true);
    const obs = this.selectedStationId
      ? this.alertService.getAlertsByStation(this.selectedStationId, this.currentPage, this.pageSize, 'SALE_ANOMALY')
      : this.alertService.getAlerts(this.currentPage, this.pageSize, undefined, 'SALE_ANOMALY');

    obs.subscribe({
      next: res => {
        if (res.success) {
          this.anomalies.set(res.data.content.map(a => this.parse(a)));
          this.totalPages = res.data.totalPages;
          this.totalElements = res.data.totalElements;
        }
        this.loading.set(false);
      },
      error: () => {
        this.notif.error('Erreur lors du chargement des anomalies');
        this.loading.set(false);
      }
    });
  }

  loadStats(): void {
    this.alertService.getAnomalyStats().subscribe({
      next: res => { if (res.success) this.stats.set(res.data); },
      error: () => console.warn('Anomaly stats unavailable')  // non-critical
    });
  }

  goToPage(page: number): void {
    this.currentPage = page;
    this.loadAnomalies();
  }

  resolve(id: number): void {
    this.alertService.resolveAlert(id).subscribe({
      next: () => {
        this.notif.success('Anomalie marquée comme résolue');
        this.loadAnomalies();
        this.loadStats();
      },
      error: () => this.notif.error('Erreur lors de la résolution')
    });
  }

  // --- message parsing ------------------------------------------------------
  private parse(alert: Alert): ParsedAnomaly {
    const msg = alert.message ?? '';
    const direction: 'high' | 'low' = msg.includes('anormalement basse') ? 'low' : 'high';
    return {
      alert,
      direction,
      quantity:  this.numAfter(msg, /\((-?\d+(?:[.,]\d+)?)L\)/),
      mean:      this.numAfter(msg, /Moyenne 90j\s*:\s*(-?\d+(?:[.,]\d+)?)L/),
      rangeLow:  this.numAfter(msg, /\[(-?\d+(?:[.,]\d+)?)L/),
      rangeHigh: this.numAfter(msg, /;\s*(-?\d+(?:[.,]\d+)?)L\]/),
      zScore:    this.numAfter(msg, /Z-score\s*:\s*(-?\d+(?:[.,]\d+)?)/),
    };
}

private numAfter(text: string, re: RegExp): number | null {
    const m = text.match(re);
    if (!m) return null;
    // JavaScript's parseFloat only understands '.', so normalize commas
    return parseFloat(m[1].replace(',', '.'));
}
  // --- UI helpers -----------------------------------------------------------
  getSeverityBadge(severity: string): string {
    return severity === 'HIGH' ? 'danger' : severity === 'MEDIUM' ? 'warning' : 'info';
  }
  getSeverityLabel(severity: string): string {
    return severity === 'HIGH' ? 'Critique' : severity === 'MEDIUM' ? 'Moyenne' : 'Faible';
  }
  getSeverityIcon(severity: string): string {
    return severity === 'HIGH' ? 'error' : severity === 'MEDIUM' ? 'warning' : 'info';
  }
  absZ(a: ParsedAnomaly): number { return Math.abs(a.zScore ?? 0); }
}
