import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AlertService, PageResponse } from '../../../core/services/alert.service';
import { StationService } from '../../../core/services/station.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Alert, Station } from '../../../core/models';
import { StationPickerComponent } from '../../../shared/components/station-picker/station-picker.component';

@Component({
  selector: 'app-alert-list',
  standalone: true,
  imports: [CommonModule, FormsModule, StationPickerComponent],
  templateUrl: './alert-list.component.html',
  styleUrls: ['./alert-list.component.scss']
})
export class AlertListComponent implements OnInit {
  private alertService = inject(AlertService);
  private stationService = inject(StationService);
  private notif = inject(NotificationService);
  private route = inject(ActivatedRoute);

  alerts: Alert[] = [];
  stations: Station[] = [];
  selectedStationId: number | null = null;
  loading = true;
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;

  ngOnInit(): void {
    this.stationService.getAllStations().subscribe({
      next: (res) => { if (res.success) this.stations = res.data; },
      error: () => this.notif.error('Impossible de charger la liste des stations')
    });
    const stationIdParam = this.route.snapshot.queryParamMap.get('stationId');
    if (stationIdParam) {
      this.selectedStationId = Number(stationIdParam);
    }
    this.loadAlerts();
  }

  onStationChange(): void {
    this.currentPage = 0;
    this.loadAlerts();
  }

  loadAlerts(): void {
    this.loading = true;
    const obs = this.selectedStationId
      ? this.alertService.getAlertsByStation(this.selectedStationId, this.currentPage)
      : this.alertService.getAlerts(this.currentPage);

    obs.subscribe({
      next: (res) => {
        if (res.success) {
          this.alerts = res.data.content;
          this.totalPages = res.data.totalPages;
          this.totalElements = res.data.totalElements;
        }
        this.loading = false;
      },
      error: () => {
        this.notif.error('Erreur lors du chargement des alertes');
        this.loading = false;
      }
    });
  }

  goToPage(page: number): void {
    this.currentPage = page;
    this.loadAlerts();
  }

  resolve(id: number): void {
    this.alertService.resolveAlert(id).subscribe({
      next: () => {
        this.notif.success('Alerte marquée comme résolue');
        this.loadAlerts();
      },
      error: () => this.notif.error('Erreur lors de la résolution de l\'alerte')
    });
  }

  getSeverityBadge(severity: string): string {
    switch (severity) { case 'HIGH': return 'danger'; case 'MEDIUM': return 'warning'; default: return 'info'; }
  }

  getSeverityLabel(severity: string): string {
    switch (severity) {
      case 'HIGH': return 'Critique';
      case 'MEDIUM': return 'Moyenne';
      case 'SALE_ANOMALY':     return 'Anomalie de vente';
      default: return 'Faible';
    }
  }

  getTypeLabel(type: string): string {
    switch (type) {
      case 'STOCK_RUPTURE':    return 'Rupture de stock';
      case 'ANOMALY':          return 'Anomalie';
      case 'STOCK_INCOHERENT': return 'Stock incohérent';
      case 'MISSING_ENTRY':    return 'Saisie manquante';
      default: return type;
    }
  }
}
