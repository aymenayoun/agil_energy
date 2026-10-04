import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Alert } from '../models';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
export interface AnomalyStats {
  totalLast7Days: number;
  maxAbsZScore: number | null;
  topStation: { stationId: number; stationName: string; count: number } | null;
}

@Injectable({ providedIn: 'root' })
export class AlertService {
  private apiUrl = `${environment.apiUrl}/alerts`;

  constructor(private http: HttpClient) {}

 getAlerts(page = 0, size = 20, stationId?: number, alertType?: string): Observable<ApiResponse<PageResponse<Alert>>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', 'createdAt,desc');
    if (stationId) params = params.set('stationId', stationId.toString());
    if (alertType) params = params.set('alertType', alertType);
    return this.http.get<ApiResponse<PageResponse<Alert>>>(this.apiUrl, { params });
  }

  getAlertsByStation(stationId: number, page = 0, size = 20, alertType?: string): Observable<ApiResponse<PageResponse<Alert>>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', 'createdAt,desc');
    if (alertType) params = params.set('alertType', alertType);
    return this.http.get<ApiResponse<PageResponse<Alert>>>(`${this.apiUrl}/station/${stationId}`, { params });
  }

  getActiveCount(): Observable<ApiResponse<number>> {
    return this.http.get<ApiResponse<number>>(`${this.apiUrl}/count`);
  }

  resolveAlert(id: number): Observable<ApiResponse<Alert>> {
    return this.http.put<ApiResponse<Alert>>(`${this.apiUrl}/${id}/resolve`, {});
  }

  getAnomalyStats(): Observable<ApiResponse<AnomalyStats>> {
    return this.http.get<ApiResponse<AnomalyStats>>(`${this.apiUrl}/stats/anomalies`);
  }
}
