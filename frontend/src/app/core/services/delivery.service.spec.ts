import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { DeliveryService } from './delivery.service';
import { environment } from '../../../environments/environment';

describe('DeliveryService', () => {
  let service: DeliveryService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/deliveries`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(DeliveryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => { expect(service).toBeTruthy(); });

  it('createDelivery() should POST to /deliveries', () => {
    const body = { stationId: 1, fuelTypeId: 2, deliveryDate: '2025-01-15', quantity: 5000 };
    service.createDelivery(body).subscribe();
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ success: true, data: {} });
  });

  it('getDeliveries() should GET without stationId filter', () => {
    service.getDeliveries().subscribe();
    const req = httpMock.expectOne(r => r.url === baseUrl && !r.params.has('stationId'));
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('getDeliveries() should GET with stationId filter', () => {
    service.getDeliveries(3).subscribe();
    const req = httpMock.expectOne(r => r.url === baseUrl && r.params.get('stationId') === '3');
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('validateDelivery() should PUT to /deliveries/:id/validate', () => {
    service.validateDelivery(10).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/10/validate`);
    expect(req.request.method).toBe('PUT');
    req.flush({ success: true, data: {} });
  });
});
