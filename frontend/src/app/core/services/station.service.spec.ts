import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { StationService } from './station.service';
import { environment } from '../../../environments/environment';

describe('StationService', () => {
  let service: StationService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/stations`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(StationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => { expect(service).toBeTruthy(); });

  it('getAllStations() should GET /stations', () => {
    service.getAllStations().subscribe();
    const req = httpMock.expectOne(r => r.url === baseUrl && !r.params.has('region'));
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('getAllStations() should include region filter', () => {
    service.getAllStations('Nord').subscribe();
    const req = httpMock.expectOne(r => r.params.get('region') === 'Nord');
    req.flush({ success: true, data: [] });
  });

  it('getStationById() should GET /stations/:id', () => {
    service.getStationById(3).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/3`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: {} });
  });

  it('createStation() should POST to /stations', () => {
    const body = { name: 'St-A', region: 'Sud' };
    service.createStation(body).subscribe();
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ success: true, data: {} });
  });

  it('updateStation() should PUT to /stations/:id', () => {
    service.updateStation(1, { name: 'St-B', region: 'Nord' }).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('PUT');
    req.flush({ success: true, data: {} });
  });

  it('deactivateStation() should PUT to /stations/:id/deactivate', () => {
    service.deactivateStation(2).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/2/deactivate`);
    expect(req.request.method).toBe('PUT');
    req.flush({ success: true });
  });

  it('activateStation() should PUT to /stations/:id/activate', () => {
    service.activateStation(2).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/2/activate`);
    expect(req.request.method).toBe('PUT');
    req.flush({ success: true });
  });

  it('addTank() should POST to /stations/tanks', () => {
    const body = { stationId: 1, fuelTypeId: 2, capacity: 10000, currentStock: 5000, criticalThreshold: 1000 };
    service.addTank(body).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/tanks`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ success: true, data: {} });
  });
});
