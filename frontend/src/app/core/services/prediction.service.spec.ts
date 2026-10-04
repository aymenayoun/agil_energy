import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PredictionService } from './prediction.service';
import { environment } from '../../../environments/environment';

describe('PredictionService', () => {
  let service: PredictionService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/predictions`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PredictionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => { expect(service).toBeTruthy(); });

  it('getPredictions() should GET /predictions/:stationId with fuelTypeId', () => {
    service.getPredictions(1, 2).subscribe();
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/1` && r.params.get('fuelTypeId') === '2'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('getLatestPredictions() should GET /predictions/:id/latest', () => {
    service.getLatestPredictions(5, 3).subscribe();
    const req = httpMock.expectOne(r => r.url === `${baseUrl}/5/latest`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('generatePrediction() should POST to /predictions/generate', () => {
    service.generatePrediction(1, 2).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/generate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ stationId: 1, fuelTypeId: 2 });
    req.flush({ success: true, data: { status: 'success' } });
  });

  it('generateBatch() should POST to /predictions/generate/batch', () => {
    service.generateBatch().subscribe();
    const req = httpMock.expectOne(`${baseUrl}/generate/batch`);
    expect(req.request.method).toBe('POST');
    req.flush({ success: true, data: {} });
  });

  it('checkIAHealth() should GET /predictions/ia/health', () => {
    service.checkIAHealth().subscribe();
    const req = httpMock.expectOne(`${baseUrl}/ia/health`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: { ia_service: 'available' } });
  });

  it('getModelMetrics() should GET with optional params', () => {
    service.getModelMetrics(1, 2).subscribe();
    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/model-metrics` &&
      r.params.get('stationId') === '1' &&
      r.params.get('fuelTypeId') === '2'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: { total: 0, metrics: [] } });
  });

  it('getRegions() should GET /predictions/regions', () => {
    service.getRegions().subscribe();
    const req = httpMock.expectOne(`${baseUrl}/regions`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('getRegionFuelTypes() should GET with encoded region', () => {
    service.getRegionFuelTypes('Grand Tunis').subscribe();
    const req = httpMock.expectOne(`${baseUrl}/regions/Grand%20Tunis/fuel-types`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('generateRegionPrediction() should POST to /predictions/generate/region', () => {
    service.generateRegionPrediction('Nord', 1).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/generate/region`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ region: 'Nord', fuelTypeId: 1 });
    req.flush({ success: true, data: {} });
  });
});
