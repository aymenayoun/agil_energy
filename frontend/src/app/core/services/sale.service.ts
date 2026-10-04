import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Sale, CreateSaleRequest } from '../models';

@Injectable({
  providedIn: 'root'
})
export class SaleService {

  private apiUrl = `${environment.apiUrl}/sales`;

  constructor(private http: HttpClient) {}

  createSale(request: CreateSaleRequest): Observable<ApiResponse<Sale>> {
    return this.http.post<ApiResponse<Sale>>(this.apiUrl, request);
  }

  getSales(stationId: number, fuelTypeId?: number, startDate?: string, endDate?: string): Observable<ApiResponse<Sale[]>> {
    let params = new HttpParams().set('stationId', stationId.toString());
    if (fuelTypeId) params = params.set('fuelTypeId', fuelTypeId.toString());
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<ApiResponse<Sale[]>>(this.apiUrl, { params });
  }

  validateSale(id: number): Observable<ApiResponse<Sale>> {
    return this.http.put<ApiResponse<Sale>>(`${this.apiUrl}/${id}/validate`, {});
  }
}
