import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ReportFormat = 'PDF' | 'EXCEL';

@Injectable({ providedIn: 'root' })
export class ReportService {

  private readonly apiUrl = `${environment.apiUrl}/reports`;

  constructor(private http: HttpClient) {}

  /** Download a monthly station report as a Blob (PDF or Excel). */
  downloadMonthlyReport(
      stationId: number, year: number, month: number, format: ReportFormat = 'PDF'
  ): Observable<HttpResponse<Blob>> {
    const params = { year: String(year), month: String(month), format };
    return this.http.get(
      `${this.apiUrl}/stations/${stationId}/monthly`,
      { params, responseType: 'blob', observe: 'response' }
    );
  }

  /** Helper: triggers a browser download from an HttpResponse<Blob>. */
  triggerBrowserDownload(response: HttpResponse<Blob>, fallbackName: string): void {
    const cd = response.headers.get('Content-Disposition') ?? '';
    const match = /filename="?([^"]+)"?/.exec(cd);
    const filename = match ? match[1] : fallbackName;

    const blob = response.body!;
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }
}
