import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models';

export interface ChatHit {
  text: string;
  source: string;
  score: number;
  metadata?: any;
}

export interface ChatResponse {
  question: string;
  answer: string | { default: string; experimental: string };
  mode: string;
  model_used?: string;
  retrieved_context?: ChatHit[];
}

export interface ExplainResponse {
  station_id: number;
  fuel_type_id: number;
  prediction: any;
  explanation: string;
}

@Injectable({ providedIn: 'root' })
export class AIService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/ai`;

  chat(question: string, mode: 'default' | 'experimental' | 'compare' = 'default'): Observable<ApiResponse<ChatResponse>> {
    return this.http.post<ApiResponse<ChatResponse>>(`${this.apiUrl}/chat`, { question, mode });
  }

  explain(stationId: number, fuelTypeId: number, mode: 'default' | 'experimental' = 'default'): Observable<ApiResponse<ExplainResponse>> {
    return this.http.post<ApiResponse<ExplainResponse>>(`${this.apiUrl}/explain`, { stationId, fuelTypeId, mode });
  }

  rebuildIndex(): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/rag/rebuild`, {});
  }
}
