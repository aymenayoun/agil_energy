import { Component, OnInit, AfterViewInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import * as L from 'leaflet';

import { PredictionService } from '../../../core/services/prediction.service';
import { StationService } from '../../../core/services/station.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Station } from '../../../core/models';

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
  selector: 'app-region-map-view',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './region-map-view.component.html',
  styleUrls: ['./region-map-view.component.scss']
})
export class RegionMapViewComponent implements OnInit, AfterViewInit, OnDestroy {

  private http = inject(HttpClient);
  private predictionService = inject(PredictionService);
  private stationService = inject(StationService);
  private notif = inject(NotificationService);

  private map!: L.Map;
  private regionsLayer?: L.GeoJSON;
  private stationsLayer?: L.LayerGroup;
  private mapReady = false;

  regions: RegionInfo[] = [];
  stations: Station[] = [];
  fuelTypes: FuelTypeInfo[] = [];

  selectedFuelTypeId: number | null = null;

  sideOpen = false;
  sideMode: 'region' | 'station' | null = null;
  sideTitle = '';
  sideLoading = false;
  sideResult: any = null;
  selectedEntityName = '';
  private _currentStationId: number | null = null;

  ngOnInit(): void {
    this.loadRegions();
    this.loadStations();
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.initMap(), 100);
  }

  ngOnDestroy(): void {
    if (this.map) this.map.remove();
  }

  // ==================== MAP INIT ====================

  private initMap(): void {
    // Fix Leaflet default icon paths (common Angular/webpack issue)
    delete (L.Icon.Default.prototype as any)._getIconUrl;
    L.Icon.Default.mergeOptions({
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
    });

    this.map = L.map('tunisia-map', {
      center: [34.0, 9.5],
      zoom: 7,
      minZoom: 6,
      maxZoom: 13
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(this.map);

    this.mapReady = true;
    this.loadGeoJson();
    this.renderStationsIfReady();
  }

  // ==================== DATA LOADING ====================

  private loadRegions(): void {
    this.predictionService.getRegions().subscribe({
      next: (res) => {
        if (res.success) {
          this.regions = res.data;
          if (this.regionsLayer) this.loadGeoJson();
        }
      }
    });
  }

  private loadStations(): void {
    this.stationService.getAllStations().subscribe({
      next: (res) => {
        if (res.success) {
          this.stations = res.data.filter(s => s.latitude && s.longitude);
          this.renderStationsIfReady();
        }
      },
      error: () => this.notif.error('Impossible de charger les stations')
    });
  }

  private loadGeoJson(): void {
    this.http.get<any>('assets/geo/tunisia-gouvernorats.geojson').subscribe({
      next: (geojson) => this.renderRegions(geojson),
      error: () => this.notif.error('Impossible de charger la carte des gouvernorats')
    });
  }

  // ==================== RENDERING ====================

  private hasDataForRegion(name: string): boolean {
    return this.regions.some(r => r.region === name);
  }
 //trying to fix persisting tooltip (hover pop up) when moving mouse
  private renderRegions(geojson: any): void {
    if (!this.mapReady) return;
    if (this.regionsLayer) this.regionsLayer.remove();

    this.regionsLayer = L.geoJSON(geojson, {
      style: (feature: any) => {
        const name = feature?.properties?.name ?? '';
        const hasData = this.hasDataForRegion(name);
        return {
          fillColor: hasData ? '#2D9CDB' : '#555',
          weight: 1.5,
          opacity: 1,
          color: '#1E1E1E',
          dashArray: hasData ? '' : '3',
          fillOpacity: hasData ? 0.3 : 0.08
        };
      },
      onEachFeature: (feature: any, layer: L.Layer) => {
        const name = feature?.properties?.name ?? '';
        const hasData = this.hasDataForRegion(name);
        const regionInfo = this.regions.find(r => r.region === name);

        const tooltip = hasData
          ? `<strong>${name}</strong><br><small>${regionInfo?.station_count ?? 0} station(s) · Cliquer pour prédire</small>`
          : `<strong>${name}</strong><br><small>Aucune station</small>`;

        (layer as L.Path).bindTooltip(tooltip, { sticky: true });

        layer.on({
          mouseover: (e: any) => {
            // Close any tooltips left open on other regions (defensive)
            this.regionsLayer?.eachLayer((other: any) => {
              if (other !== e.target) other.closeTooltip();
            });

            e.target.setStyle({ weight: 3, fillOpacity: hasData ? 0.5 : 0.15 });

            // NOTE: Do NOT call e.target.bringToFront() here.
            // Reordering the SVG DOM during hover causes Leaflet's
            // mouseout to misfire, leaving tooltips stuck open.
            // Keeping station markers on top is enough.
            if (this.stationsLayer) {
              this.stationsLayer.eachLayer(l => (l as L.CircleMarker).bringToFront());
            }
          },
          mouseout: (e: any) => {
            this.regionsLayer?.resetStyle(e.target);
            e.target.closeTooltip();
          },
          click: () => {
            if (hasData) this.onRegionClick(name);
            else this.notif.error(`Aucune donnée pour ${name}`);
          }
        });
      }
    }).addTo(this.map);

    // Safety net: when the cursor leaves the map entirely, close any
    // region tooltip that may still be open due to a missed mouseout.
    this.map.off('mouseout', this.closeAllRegionTooltips);
    this.map.on('mouseout', this.closeAllRegionTooltips);
  }

  private closeAllRegionTooltips = (): void => {
    this.regionsLayer?.eachLayer((l: any) => l.closeTooltip());
  };

  private renderStationsIfReady(): void {
    if (!this.mapReady || !this.stations.length) return;

    if (!this.stationsLayer) {
      this.stationsLayer = L.layerGroup().addTo(this.map);
    } else {
      this.stationsLayer.clearLayers();
    }

    for (const st of this.stations) {
      const marker = L.circleMarker([st.latitude, st.longitude], {
        radius: 7,
        fillColor: st.status === 'ACTIVE' ? '#F2C94C' : '#888',
        color: '#fff',
        weight: 2,
        opacity: 1,
        fillOpacity: 0.9
      });

      const tankInfo = (st.tanks || []).map(t => t.fuelTypeName).join(', ');
      marker.bindTooltip(
        `<strong>${st.name}</strong><br>
         <small>${st.region} · ${st.status}</small><br>
         <small>${tankInfo || 'Aucun réservoir'}</small><br>
         <small>Cliquer pour prédire</small>`,
        { sticky: true }
      );

      marker.on('click', () => this.onStationClick(st));
      marker.on('mouseout', (e: any) => e.target.closeTooltip());
      this.stationsLayer.addLayer(marker);
    }

    this.stationsLayer.eachLayer(l => (l as L.CircleMarker).bringToFront());
  }

  // ==================== CLICK HANDLERS ====================

  onRegionClick(region: string): void {
    this.sideMode = 'region';
    this.selectedEntityName = region;
    this.sideTitle = region;
    this.sideResult = null;
    this.selectedFuelTypeId = null;
    this.sideOpen = true;

    this.predictionService.getRegionFuelTypes(region).subscribe({
      next: (res) => {
        if (res.success) {
          this.fuelTypes = res.data;
          if (this.fuelTypes.length) {
            this.selectedFuelTypeId = this.fuelTypes[0].fuel_type_id;
          }
        }
      }
    });
  }

  onStationClick(station: Station): void {
    this.sideMode = 'station';
    this.selectedEntityName = station.name;
    this.sideTitle = station.name;
    this.sideResult = null;
    this.selectedFuelTypeId = null;
    this.sideOpen = true;
    this._currentStationId = station.id;

    this.fuelTypes = (station.tanks || []).map(t => ({
      fuel_type_id: t.fuelTypeId,
      fuel_type_name: t.fuelTypeName
    }));
    if (this.fuelTypes.length) {
      this.selectedFuelTypeId = this.fuelTypes[0].fuel_type_id;
    }
  }

  closeSide(): void {
    this.sideOpen = false;
    this.sideMode = null;
    this.sideResult = null;
    this.selectedEntityName = '';
    this._currentStationId = null;
  }

  // ==================== PREDICTION ====================

  generate(): void {
    if (!this.selectedFuelTypeId) return;
    this.sideLoading = true;
    this.sideResult = null;

    if (this.sideMode === 'region') {
      this.predictionService.generateRegionPrediction(this.selectedEntityName, this.selectedFuelTypeId).subscribe({
        next: (res) => {
          this.sideLoading = false;
          if (res.success) this.sideResult = res.data;
        },
        error: () => {
          this.sideLoading = false;
          this.notif.error('Erreur de prédiction régionale');
        }
      });
    } else if (this.sideMode === 'station' && this._currentStationId) {
      this.predictionService.generatePrediction(this._currentStationId, this.selectedFuelTypeId).subscribe({
        next: (res) => {
          this.sideLoading = false;
          if (res.success) this.sideResult = res.data;
        },
        error: () => {
          this.sideLoading = false;
          this.notif.error('Erreur de prédiction station');
        }
      });
    }
  }

  // ==================== HELPERS ====================

  getRiskBadge(risk: string): string {
    switch (risk) { case 'HIGH': return 'danger'; case 'MEDIUM': return 'warning'; default: return 'success'; }
  }

  getForecastAverage(): number {
    const f = this.sideResult?.forecast_7_days;
    if (!f?.length) return 0;
    return f.reduce((a: number, b: number) => a + b, 0) / f.length;
  }

  getForecastTotal(): number {
    const f = this.sideResult?.forecast_7_days;
    if (!f?.length) return 0;
    return f.reduce((a: number, b: number) => a + b, 0);
  }
}
