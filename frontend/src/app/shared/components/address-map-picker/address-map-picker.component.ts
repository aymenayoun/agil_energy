import {
  Component, Input, Output, EventEmitter,
  AfterViewInit, OnDestroy, OnChanges, SimpleChanges,
  ElementRef, ViewChild, HostListener
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import * as L from 'leaflet';
import { Subject, of } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

export interface LocationResult {
  address: string;
  latitude: number;
  longitude: number;
}

interface NominatimResult {
  display_name: string;
  lat: string;
  lon: string;
}

@Component({
  selector: 'app-address-map-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './address-map-picker.component.html',
  styleUrls: ['./address-map-picker.component.scss']
})
export class AddressMapPickerComponent implements AfterViewInit, OnDestroy, OnChanges {

  @Input() address = '';
  @Input() latitude: number | undefined;
  @Input() longitude: number | undefined;
  @Input() mapHeight = '260px';
  @Input() mapId = 'address-map-' + Math.random().toString(36).substring(2, 9);

  @Output() locationSelected = new EventEmitter<LocationResult>();

  @ViewChild('searchInput') searchInputRef!: ElementRef<HTMLInputElement>;

  query = '';
  suggestions: NominatimResult[] = [];
  showSuggestions = false;
  searching = false;

  private map: L.Map | null = null;
  private marker: L.Marker | null = null;
  private searchSubject = new Subject<string>();
  private initialized = false;

  constructor(private http: HttpClient) {
    this.searchSubject.pipe(
      debounceTime(400),
      distinctUntilChanged(),
      switchMap(q => {
        if (!q || q.length < 3) {
          this.suggestions = [];
          this.showSuggestions = false;
          this.searching = false;
          return of([]);
        }
        this.searching = true;
        return this.http.get<NominatimResult[]>(
          'https://nominatim.openstreetmap.org/search', {
            params: {
              q: q,
              format: 'json',
              addressdetails: '1',
              limit: '6',
              countrycodes: 'tn',
              'accept-language': 'fr'
            }
          }
        ).pipe(catchError(() => {
          this.searching = false;
          return of([]);
        }));
      })
    ).subscribe(results => {
      this.suggestions = results;
      this.showSuggestions = results.length > 0;
      this.searching = false;
    });
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.initMap(), 100);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.initialized) return;

    // If address input changed externally, sync the query
    if (changes['address'] && !changes['address'].firstChange) {
      this.query = this.address || '';
    }

    // If lat/lng changed externally, update the marker
    if ((changes['latitude'] || changes['longitude']) && this.latitude && this.longitude) {
      this.setMarker(this.latitude, this.longitude);
      this.map?.setView([this.latitude, this.longitude], 14);
    }
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }

  // ── Search ──

  onSearchInput(): void {
    this.searchSubject.next(this.query);
  }

  onSearchFocus(): void {
    if (this.suggestions.length > 0) {
      this.showSuggestions = true;
    }
  }

  selectSuggestion(result: NominatimResult): void {
    const lat = parseFloat(result.lat);
    const lng = parseFloat(result.lon);
    this.query = result.display_name;
    this.showSuggestions = false;
    this.suggestions = [];

    this.setMarker(lat, lng);
    this.map?.flyTo([lat, lng], 16, { duration: 0.6 });

    this.locationSelected.emit({
      address: result.display_name,
      latitude: lat,
      longitude: lng
    });
  }

  clearSearch(): void {
    this.query = '';
    this.suggestions = [];
    this.showSuggestions = false;
    if (this.marker) {
      this.marker.remove();
      this.marker = null;
    }
    this.locationSelected.emit({
      address: '',
      latitude: 0,
      longitude: 0
    });
  }

  @HostListener('document:click', ['$event'])
  onDocClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.address-picker-wrapper')) {
      this.showSuggestions = false;
    }
  }

  // ── Map ──

  private initMap(): void {
    const container = document.getElementById(this.mapId);
    if (!container) return;

    delete (L.Icon.Default.prototype as any)._getIconUrl;
    L.Icon.Default.mergeOptions({
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
    });

    const initialLat = this.latitude || 34.0;
    const initialLng = this.longitude || 9.5;
    const initialZoom = (this.latitude && this.longitude) ? 14 : 7;

    this.map = L.map(this.mapId, {
      center: [initialLat, initialLng],
      zoom: initialZoom,
      minZoom: 5,
      maxZoom: 18
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>'
    }).addTo(this.map);

    // If we have initial coordinates, place a marker
    if (this.latitude && this.longitude) {
      this.setMarker(this.latitude, this.longitude);
    }

    // Sync input
    this.query = this.address || '';

    // Click on map to pick location
    this.map.on('click', (e: L.LeafletMouseEvent) => {
      const { lat, lng } = e.latlng;
      this.setMarker(lat, lng);
      this.reverseGeocode(lat, lng);
    });

    this.initialized = true;
  }

  private setMarker(lat: number, lng: number): void {
    if (this.marker) {
      this.marker.setLatLng([lat, lng]);
    } else if (this.map) {
      this.marker = L.marker([lat, lng], { draggable: true }).addTo(this.map);

      // Drag end: reverse geocode new position
      this.marker.on('dragend', () => {
        const pos = this.marker!.getLatLng();
        this.reverseGeocode(pos.lat, pos.lng);
      });
    }
  }

  private reverseGeocode(lat: number, lng: number): void {
    this.http.get<any>(
      'https://nominatim.openstreetmap.org/reverse', {
        params: {
          lat: lat.toString(),
          lon: lng.toString(),
          format: 'json',
          'accept-language': 'fr'
        }
      }
    ).pipe(
      catchError(() => of(null))
    ).subscribe(result => {
      const address = result?.display_name || `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
      this.query = address;
      this.locationSelected.emit({
        address,
        latitude: parseFloat(lat.toFixed(8)),
        longitude: parseFloat(lng.toFixed(8))
      });
    });
  }
}
