import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Tank, StockAdjustmentRequest, StockMovement } from '../models';

@Injectable({
  providedIn: 'root'
})
export class StockService {

  private apiUrl = `${environment.apiUrl}/stocks`;

  constructor(private http: HttpClient) {}

  getStocksByStation(stationId: number): Observable<ApiResponse<Tank[]>> {
    return this.http.get<ApiResponse<Tank[]>>(`${this.apiUrl}/${stationId}`);
  }

  adjustStock(request: StockAdjustmentRequest): Observable<ApiResponse<Tank>> {
    return this.http.put<ApiResponse<Tank>>(`${this.apiUrl}/adjust`, request);
  }

  getCriticalTanks(): Observable<ApiResponse<Tank[]>> {
    return this.http.get<ApiResponse<Tank[]>>(`${this.apiUrl}/critical`);
  }

  getMovements(tankId: number): Observable<ApiResponse<StockMovement[]>> {
    return this.http.get<ApiResponse<StockMovement[]>>(`${this.apiUrl}/movements/${tankId}`);
  }
}
