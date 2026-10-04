import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Delivery, CreateDeliveryRequest } from '../models';

@Injectable({
  providedIn: 'root'
})
export class DeliveryService {

  private apiUrl = `${environment.apiUrl}/deliveries`;

  constructor(private http: HttpClient) {}

  createDelivery(request: CreateDeliveryRequest): Observable<ApiResponse<Delivery>> {
    return this.http.post<ApiResponse<Delivery>>(this.apiUrl, request);
  }

  getDeliveries(stationId?: number): Observable<ApiResponse<Delivery[]>> {
    let params = new HttpParams();
    if (stationId) params = params.set('stationId', stationId.toString());
    return this.http.get<ApiResponse<Delivery[]>>(this.apiUrl, { params });
  }

  validateDelivery(id: number): Observable<ApiResponse<Delivery>> {
    return this.http.put<ApiResponse<Delivery>>(`${this.apiUrl}/${id}/validate`, {});
  }
}
