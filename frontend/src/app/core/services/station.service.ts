import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Station, CreateStationRequest, UpdateStationRequest, Tank } from '../models';

export interface CreateTankRequest {
  stationId: number;
  fuelTypeId: number;
  capacity: number;
  currentStock: number;
  criticalThreshold: number;
}

@Injectable({
  providedIn: 'root'
})
export class StationService {

  private apiUrl = `${environment.apiUrl}/stations`;

  constructor(private http: HttpClient) {}

  getAllStations(region?: string): Observable<ApiResponse<Station[]>> {
    let params = new HttpParams();
    if (region) params = params.set('region', region);
    return this.http.get<ApiResponse<Station[]>>(this.apiUrl, { params });
  }

  getStationById(id: number): Observable<ApiResponse<Station>> {
    return this.http.get<ApiResponse<Station>>(`${this.apiUrl}/${id}`);
  }

  createStation(request: CreateStationRequest): Observable<ApiResponse<Station>> {
    return this.http.post<ApiResponse<Station>>(this.apiUrl, request);
  }

  updateStation(id: number, request: UpdateStationRequest): Observable<ApiResponse<Station>> {
    return this.http.put<ApiResponse<Station>>(`${this.apiUrl}/${id}`, request);
  }

  deactivateStation(id: number): Observable<ApiResponse<void>> {
    return this.http.put<ApiResponse<void>>(`${this.apiUrl}/${id}/deactivate`, {});
  }

  activateStation(id: number): Observable<ApiResponse<void>> {
    return this.http.put<ApiResponse<void>>(`${this.apiUrl}/${id}/activate`, {});
  }

  addTank(request: CreateTankRequest): Observable<ApiResponse<Tank>> {
    return this.http.post<ApiResponse<Tank>>(`${this.apiUrl}/tanks`, request);
  }
}
