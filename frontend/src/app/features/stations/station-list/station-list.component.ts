import { Component, OnInit, AfterViewChecked } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import * as L from 'leaflet';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StationService, CreateTankRequest } from '../../../core/services/station.service';
import { AuthService } from '../../../core/services/auth.service';
import { Station, CreateStationRequest, UpdateStationRequest } from '../../../core/models';
import { NotificationService } from '../../../core/services/notification.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { StationPickerComponent } from '../../../shared/components/station-picker/station-picker.component';
import { AddressMapPickerComponent, LocationResult } from '../../../shared/components/address-map-picker/address-map-picker.component';
import { ReportService, ReportFormat } from '../../../core/services/report.service';
@Component({
  selector: 'app-station-list',
  standalone: true,
  imports: [CommonModule, FormsModule, StationPickerComponent, AddressMapPickerComponent],
  templateUrl: './station-list.component.html',
  styleUrls: ['./station-list.component.scss']
})
export class StationListComponent implements OnInit, AfterViewChecked {

  stations: Station[] = [];
  regions: string[] = [];
  regionFilter = '';
  searchStationId: number | null = null;
  loading = true;
  isAdmin = false;
  canExportReport = false;
  showReportModal = false;
  reportStation: Station | null = null;
  reportYear: number = new Date().getFullYear();
  reportMonth: number = new Date().getMonth() + 1;
  reportLoading = false;
  showForm = false;
  formError = '';
  newStation: CreateStationRequest = { name: '', region: '' };

  confirmingId: number | null = null;

  showTankForm = false;
  selectedStation: Station | null = null;
  newTank: CreateTankRequest = { stationId: 0, fuelTypeId: 0, capacity: 0, currentStock: 0, criticalThreshold: 0 };
  tankError = '';
  fuelTypesList: { id: number; name: string }[] = [];

  // ==================== ADDRESS MAP PICKER HANDLERS ====================

  onCreateLocationSelected(loc: LocationResult): void {
    this.newStation.address = loc.address;
    this.newStation.latitude = loc.latitude || undefined;
    this.newStation.longitude = loc.longitude || undefined;
  }

  onEditLocationSelected(loc: LocationResult): void {
    this.editStation.address = loc.address;
    this.editStation.latitude = loc.latitude || undefined;
    this.editStation.longitude = loc.longitude || undefined;
  }

  // -------- Edit Station --------
  showEditStation = false;
  editStation: { id: number } & UpdateStationRequest = this.emptyEditStation();
  editStationError = '';
  availableManagers: { id: number; name: string; stationName?: string | null }[] = [];
  // Map view
  activeView: 'list' | 'map' = 'list';
  private map: L.Map | null = null;
  private mapInitialized = false;
  private stationsLayer?: L.LayerGroup;
  selectedMapStation: Station | null = null;
  constructor(
    private stationService: StationService,
    private authService: AuthService,
    private notif: NotificationService,
    private reportService: ReportService,
    private http: HttpClient,
    private route: ActivatedRoute
  ) {
      this.isAdmin = this.authService.hasRole('ADMIN');
      this.canExportReport = this.authService.hasRole('ADMIN') || this.authService.hasRole('MANAGER');
  }
  ngOnInit(): void {
    this.loadStations();
    this.http.get<any>(`${environment.apiUrl}/fuel-types`).subscribe(res => {
      if (res.success) this.fuelTypesList = res.data;
    });
    if (this.isAdmin) {
      this.loadAvailableManagers();
    }
  }

  loadStations(): void {
    this.loading = true;
    this.stationService.getAllStations(this.regionFilter || undefined).subscribe({
      next: (res) => {
        if (res.success) {
          this.stations = res.data;
          this.regions = [...new Set(res.data.map(s => s.region))];
          const stationIdParam = this.route.snapshot.queryParamMap.get('stationId');
          if (stationIdParam) {
            this.searchStationId = Number(stationIdParam);
            this.onSearchStationChange();
          }
        }
        this.loading = false;
      },
      error: () => { this.loading = false; this.notif.error('Impossible de charger les stations'); }
    });
  }
  onRegionFilterChange(): void {
  this.searchStationId = null;
  this.loadStations();
  }

  createStation(): void {
    if (!this.newStation.name || !this.newStation.region) {
      this.formError = 'Le nom et la région sont obligatoires';
      return;
    }
    this.stationService.createStation(this.newStation).subscribe({
      next: () => {
        this.showForm = false;
        this.newStation = { name: '', region: '' };
        this.formError = '';
        this.loadStations();
      },
      error: (err) => {
        this.formError = err.error?.message || 'Erreur lors de la création';
        this.notif.error(this.formError);
      }
    });
  }

  deactivate(id: number): void {
    this.confirmingId = null;
    this.stationService.deactivateStation(id).subscribe(() => this.loadStations());
  }

  activate(id: number): void {
    this.stationService.activateStation(id).subscribe(() => this.loadStations());
  }

  openAddTank(station: Station): void {
    this.selectedStation = station;
    this.newTank = { stationId: station.id, fuelTypeId: 0, capacity: 0, currentStock: 0, criticalThreshold: 0 };
    this.tankError = '';
    this.showTankForm = true;
  }

  submitTank(): void {
    if (!this.newTank.fuelTypeId || this.newTank.capacity <= 0) {
      this.tankError = 'Veuillez sélectionner un carburant et indiquer une capacité valide';
      return;
    }
    if (this.newTank.currentStock > this.newTank.capacity) {
      this.tankError = 'Le stock initial ne peut pas dépasser la capacité';
      return;
    }
    if (this.newTank.criticalThreshold > this.newTank.capacity) {
      this.tankError = 'Le seuil critique ne peut pas dépasser la capacité';
      return;
    }


    this.stationService.addTank(this.newTank).subscribe({
      next: () => {
        this.showTankForm = false;
        this.loadStations();
      },
      error: (err) => this.tankError = err.error?.message || 'Erreur lors de l\'ajout du réservoir'
    });
  }
  // ==================== MAP VIEW ====================

  ngAfterViewChecked(): void {
    if (this.activeView === 'map' && !this.mapInitialized) {
      const container = document.getElementById('stations-map');
      if (container) {
        this.initStationMap();
        this.mapInitialized = true;
      }
    }
  }

  switchView(view: 'list' | 'map'): void {
    this.activeView = view;
    if (view === 'list') {
      this.destroyMap();
    } else {
      this.mapInitialized = false; // Will init in ngAfterViewChecked
    }
  }

  private initStationMap(): void {
    delete (L.Icon.Default.prototype as any)._getIconUrl;
    L.Icon.Default.mergeOptions({
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
    });

    this.map = L.map('stations-map', {
      center: [34.0, 9.5],
      zoom: 7,
      minZoom: 6,
      maxZoom: 15
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(this.map);

    this.renderStationMarkers();
  }

  private renderStationMarkers(): void {
    if (!this.map) return;

    if (!this.stationsLayer) {
      this.stationsLayer = L.layerGroup().addTo(this.map);
    } else {
      this.stationsLayer.clearLayers();
    }

    const filtered = this.displayedStations;

    for (const st of filtered) {
      if (!st.latitude || !st.longitude) continue;

      const color = this.getStationMarkerColor(st);
      const marker = L.circleMarker([st.latitude, st.longitude], {
        radius: 9,
        fillColor: color,
        color: '#fff',
        weight: 2.5,
        opacity: 1,
        fillOpacity: 0.9
      });

      // Build tooltip with tank info
      let tankHtml = '';
      if (st.tanks?.length) {
        tankHtml = st.tanks.map(t => {
          const pct = t.stockPercentage;
          const barColor = t.critical ? '#EB5757' : pct < 40 ? '#F2994A' : '#27AE60';
          return `<div style="display:flex;align-items:center;gap:6px;margin:2px 0">
            <span style="font-size:11px;min-width:60px">${t.fuelTypeName}</span>
            <div style="width:50px;height:5px;background:#333;border-radius:3px;overflow:hidden">
              <div style="width:${pct}%;height:100%;background:${barColor};border-radius:3px"></div>
            </div>
            <span style="font-size:10px;color:#aaa">${Math.round(pct)}%</span>
          </div>`;
        }).join('');
      } else {
        tankHtml = '<span style="font-size:11px;color:#888">Aucun réservoir</span>';
      }

      marker.bindTooltip(
        `<div style="min-width:160px">
          <strong>${st.name}</strong><br>
          <span style="font-size:11px;color:#aaa">${st.region} · </span>
          <span style="font-size:11px;color:${st.status === 'ACTIVE' ? '#27AE60' : '#EB5757'}">${st.status}</span>
          <div style="margin-top:4px;border-top:1px solid #333;padding-top:4px">
            ${tankHtml}
          </div>
        </div>`,
        { sticky: true }
      );

      marker.on('click', () => {
        this.selectedMapStation = st;
        if (this.map) this.map.flyTo([st.latitude, st.longitude], 11, { duration: 0.5 });
      });

      this.stationsLayer.addLayer(marker);
    }

    // Fit bounds if we have stations
    if (filtered.length > 0) {
      const validStations = filtered.filter(s => s.latitude && s.longitude);
      if (validStations.length > 0) {
        const bounds = L.latLngBounds(validStations.map(s => [s.latitude, s.longitude] as [number, number]));
        this.map.fitBounds(bounds, { padding: [30, 30], maxZoom: 10 });
      }
    }
  }

  private getStationMarkerColor(st: Station): string {
    if (st.status !== 'ACTIVE') return '#888';
    if (!st.tanks?.length) return '#F2C94C';

    // Check if any tank is critical
    const hasCritical = st.tanks.some(t => t.critical);
    const hasWarning = st.tanks.some(t => !t.critical && t.stockPercentage < 40);

    if (hasCritical) return '#EB5757';   // Red
    if (hasWarning) return '#F2994A';     // Orange
    return '#27AE60';                     // Green
  }

  closeMapDetail(): void {
    this.selectedMapStation = null;
  }

  private destroyMap(): void {
    if (this.map) {
      this.map.remove();
      this.map = null;
      this.mapInitialized = false;
      this.stationsLayer = undefined;
      this.selectedMapStation = null;
    }
  }

  getOverallStockStatus(station: Station): string {
    if (!station.tanks?.length) return 'empty';
    if (station.tanks.some(t => t.critical)) return 'critical';
    if (station.tanks.some(t => t.stockPercentage < 40)) return 'warning';
    return 'ok';
  }

  // ==================== IN-PAGE SEARCH FILTER ====================
    /** Stations actually rendered, after applying the in-page search filter. */
  get displayedStations(): Station[] {
    if (this.searchStationId === null) return this.stations;
    return this.stations.filter(s => s.id === this.searchStationId);
  }

  onSearchStationChange(): void {
    // Re-render map markers (if we are on the map view) to reflect the filter.
    if (this.activeView === 'map' && this.map) {
      this.renderStationMarkers();
    }
  }

  openReportModal(station: Station): void {
    this.reportStation = station;
    this.reportYear = new Date().getFullYear();
    this.reportMonth = new Date().getMonth() + 1;
    this.showReportModal = true;
  }

  closeReportModal(): void {
    this.showReportModal = false;
    this.reportStation = null;
  }

  downloadReport(format: ReportFormat): void {
    if (!this.reportStation) return;
    this.reportLoading = true;
    this.reportService
      .downloadMonthlyReport(this.reportStation.id, this.reportYear, this.reportMonth, format)
      .subscribe({
        next: (resp) => {
          const ext = format === 'EXCEL' ? 'xlsx' : 'pdf';
          const fallback = `rapport_station_${this.reportStation!.id}_${this.reportYear}_${String(this.reportMonth).padStart(2, '0')}.${ext}`;
          this.reportService.triggerBrowserDownload(resp, fallback);
          this.reportLoading = false;
          this.closeReportModal();
          this.notif.success(`Rapport ${format} téléchargé`);
        },
        error: (err) => {
          this.reportLoading = false;
          this.notif.error(err.error?.message ?? 'Erreur lors de la génération du rapport');
        }
      });
  }

  // ==================== EDIT STATION ====================

  private loadAvailableManagers(): void {
    this.http.get<any>(`${environment.apiUrl}/users`).subscribe({
      next: (res) => {
        if (res.success) {
          this.availableManagers = (res.data as any[])
            .filter(u => u.roleName === 'STATION_MANAGER' && u.status === 'ACTIVE')
            .map(u => ({ id: u.id, name: u.name, stationName: u.stationName }));
        }
      },
      error: () => { /* silent */ }
    });
  }

  openEditStation(station: Station): void {
    this.editStation = {
      id: station.id,
      name: station.name,
      region: station.region,
      address: station.address || '',
      latitude: station.latitude || undefined,
      longitude: station.longitude || undefined,
      managerId: station.managerId || undefined
    };
    this.editStationError = '';
    this.showEditStation = true;
    // Refresh manager list so we have latest data
    this.loadAvailableManagers();
  }

  submitEditStation(): void {
    this.editStationError = '';
    if (!this.editStation.name || !this.editStation.region) {
      this.editStationError = 'Le nom et la région sont obligatoires';
      return;
    }

    const payload: UpdateStationRequest = {
      name: this.editStation.name,
      region: this.editStation.region,
      address: this.editStation.address || undefined,
      latitude: this.editStation.latitude || undefined,
      longitude: this.editStation.longitude || undefined,
      managerId: this.editStation.managerId || undefined
    };

    this.stationService.updateStation(this.editStation.id, payload).subscribe({
      next: (res) => {
        if (res.success) {
          this.notif.success('Station mise à jour avec succès');
          this.showEditStation = false;
          this.loadStations();
          // If we were viewing this station on the map, update it
          if (this.selectedMapStation?.id === this.editStation.id) {
            this.selectedMapStation = res.data;
          }
        }
      },
      error: (err) => {
        this.editStationError = err.error?.message || 'Erreur lors de la mise à jour';
        this.notif.error(this.editStationError);
      }
    });
  }

  closeEditStation(): void {
    this.showEditStation = false;
    this.editStationError = '';
  }

  getManagerLabel(managerId: number | undefined): string {
    if (!managerId) return 'Aucun';
    const m = this.availableManagers.find(u => u.id === managerId);
    return m ? m.name : `ID #${managerId}`;
  }

  private emptyEditStation(): { id: number } & UpdateStationRequest {
    return { id: 0, name: '', region: '', address: '', latitude: undefined, longitude: undefined, managerId: undefined };
  }

}
