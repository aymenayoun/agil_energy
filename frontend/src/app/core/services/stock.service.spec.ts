import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { StockService } from './stock.service';
import { environment } from '../../../environments/environment';

describe('StockService', () => {
  let service: StockService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/stocks`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(StockService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => { expect(service).toBeTruthy(); });

  it('getStocksByStation() should GET /stocks/:stationId', () => {
    service.getStocksByStation(4).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/4`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('adjustStock() should PUT to /stocks/adjust', () => {
    const body = { tankId: 1, adjustedQuantity: 500, reason: 'correction' };
    service.adjustStock(body as any).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/adjust`);
    expect(req.request.method).toBe('PUT');
    req.flush({ success: true, data: {} });
  });

  it('getCriticalTanks() should GET /stocks/critical', () => {
    service.getCriticalTanks().subscribe();
    const req = httpMock.expectOne(`${baseUrl}/critical`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('getMovements() should GET /stocks/movements/:tankId', () => {
    service.getMovements(7).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/movements/7`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });
});
