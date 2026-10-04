import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ReportService } from './report.service';
import { environment } from '../../../environments/environment';

describe('ReportService', () => {
  let service: ReportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ReportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('downloadMonthlyReport() should GET with correct params', () => {
    service.downloadMonthlyReport(1, 2025, 3, 'PDF').subscribe();
    const req = httpMock.expectOne(r =>
      r.url === `${environment.apiUrl}/reports/stations/1/monthly` &&
      r.params.get('year') === '2025' &&
      r.params.get('month') === '3' &&
      r.params.get('format') === 'PDF'
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['test']));
  });

  it('downloadMonthlyReport() should default to PDF format', () => {
    service.downloadMonthlyReport(2, 2024, 12).subscribe();
    const req = httpMock.expectOne(r => r.url.includes('/reports/stations/2/monthly'));
    expect(req.request.params.get('format')).toBe('PDF');
    req.flush(new Blob());
  });
});
