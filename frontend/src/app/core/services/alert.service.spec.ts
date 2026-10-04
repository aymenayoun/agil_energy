import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AlertService } from './alert.service';
import { environment } from '../../../environments/environment';

describe('AlertService', () => {
  let service: AlertService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/alerts`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AlertService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => { expect(service).toBeTruthy(); });

  it('getAlerts() should GET with pagination params', () => {
    service.getAlerts(2, 10).subscribe();
    const req = httpMock.expectOne(r =>
      r.url === baseUrl &&
      r.params.get('page') === '2' &&
      r.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: { content: [], totalPages: 0, totalElements: 0 } });
  });

  it('getAlerts() should include optional stationId and alertType', () => {
    service.getAlerts(0, 20, 5, 'SALE_ANOMALY').subscribe();
    const req = httpMock.expectOne(r =>
      r.params.get('stationId') === '5' &&
      r.params.get('alertType') === 'SALE_ANOMALY'
    );
    req.flush({ success: true, data: { content: [] } });
  });

  it('getAlertsByStation() should GET with station path param', () => {
    service.getAlertsByStation(3, 0, 20).subscribe();
    const req = httpMock.expectOne(r => r.url === `${baseUrl}/station/3`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: { content: [] } });
  });

  it('getActiveCount() should GET /alerts/count', () => {
    service.getActiveCount().subscribe();
    const req = httpMock.expectOne(`${baseUrl}/count`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: 42 });
  });

  it('resolveAlert() should PUT to /alerts/:id/resolve', () => {
    service.resolveAlert(7).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/7/resolve`);
    expect(req.request.method).toBe('PUT');
    req.flush({ success: true, data: {} });
  });

  it('getAnomalyStats() should GET /alerts/stats/anomalies', () => {
    service.getAnomalyStats().subscribe();
    const req = httpMock.expectOne(`${baseUrl}/stats/anomalies`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: { totalLast7Days: 3 } });
  });
});
