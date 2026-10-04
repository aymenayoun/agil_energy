import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { FuelTypeService } from './fuel-type.service';
import { environment } from '../../../environments/environment';

describe('FuelTypeService', () => {
  let service: FuelTypeService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/fuel-types`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(FuelTypeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => { expect(service).toBeTruthy(); });

  it('getAllFuelTypes() should GET /fuel-types', () => {
    service.getAllFuelTypes().subscribe();
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [] });
  });

  it('getFuelTypeById() should GET /fuel-types/:id', () => {
    service.getFuelTypeById(3).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/3`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: {} });
  });

  it('createFuelType() should POST to /fuel-types', () => {
    const body = { name: 'Gasoil', description: 'Diesel' };
    service.createFuelType(body).subscribe();
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ success: true, data: {} });
  });

  it('updateFuelType() should PUT to /fuel-types/:id', () => {
    service.updateFuelType(1, { name: 'Gasoil 50' }).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('PUT');
    req.flush({ success: true, data: {} });
  });

  it('deleteFuelType() should DELETE /fuel-types/:id', () => {
    service.deleteFuelType(2).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/2`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true });
  });
});
