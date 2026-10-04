import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, FuelType, CreateFuelTypeRequest, UpdateFuelTypeRequest } from '../models';

@Injectable({
  providedIn: 'root'
})
export class FuelTypeService {

  private apiUrl = `${environment.apiUrl}/fuel-types`;

  constructor(private http: HttpClient) {}

  getAllFuelTypes(): Observable<ApiResponse<FuelType[]>> {
    return this.http.get<ApiResponse<FuelType[]>>(this.apiUrl);
  }

  getFuelTypeById(id: number): Observable<ApiResponse<FuelType>> {
    return this.http.get<ApiResponse<FuelType>>(`${this.apiUrl}/${id}`);
  }

  createFuelType(request: CreateFuelTypeRequest): Observable<ApiResponse<FuelType>> {
    return this.http.post<ApiResponse<FuelType>>(this.apiUrl, request);
  }

  updateFuelType(id: number, request: UpdateFuelTypeRequest): Observable<ApiResponse<FuelType>> {
    return this.http.put<ApiResponse<FuelType>>(`${this.apiUrl}/${id}`, request);
  }

  deleteFuelType(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.apiUrl}/${id}`);
  }
}
