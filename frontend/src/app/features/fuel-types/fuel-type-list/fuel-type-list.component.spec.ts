import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { FuelTypeListComponent } from './fuel-type-list.component';
import { NotificationService } from '../../../core/services/notification.service';

const mockNotifService = {
  success: (_msg: string) => {},
  error: (_msg: string) => {},
  info: (_msg: string) => {},
};

describe('FuelTypeListComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FuelTypeListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: mockNotifService },
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create and load fuel types on init', () => {
    const fixture = TestBed.createComponent(FuelTypeListComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne(r => r.url.includes('/fuel-types'));
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: [{ id: 1, name: 'Gasoil', description: 'Diesel', createdAt: '' }] });

    expect(fixture.componentInstance.fuelTypes.length).toBe(1);
    expect(fixture.componentInstance.loading).toBeFalse();
  });

  it('should show formError when creating with empty name', () => {
    const fixture = TestBed.createComponent(FuelTypeListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/fuel-types')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.newFuelType = { name: '  ', description: '' };
    comp.createFuelType();
    expect(comp.formError).toBeTruthy();
  });

  it('createFuelType should POST and reload on success', () => {
    const fixture = TestBed.createComponent(FuelTypeListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/fuel-types')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.newFuelType = { name: 'SP98', description: 'Sans plomb 98' };
    comp.createFuelType();

    const post = httpMock.expectOne(r => r.method === 'POST' && r.url.includes('/fuel-types'));
    expect(post.request.body).toEqual({ name: 'SP98', description: 'Sans plomb 98' });
    post.flush({ success: true, data: { id: 2, name: 'SP98', description: 'Sans plomb 98', createdAt: '' } });

    // reload GET
    httpMock.expectOne(r => r.method === 'GET' && r.url.includes('/fuel-types')).flush({ success: true, data: [] });
    expect(comp.showForm).toBeFalse();
  });

  it('openEdit should populate editFuelType and open modal', () => {
    const fixture = TestBed.createComponent(FuelTypeListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/fuel-types')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.openEdit({ id: 5, name: 'Gasoil', description: 'Diesel', createdAt: '' });
    expect(comp.showEditForm).toBeTrue();
    expect(comp.editFuelType.id).toBe(5);
    expect(comp.editFuelType.name).toBe('Gasoil');
  });

  it('submitEdit should show error when name empty', () => {
    const fixture = TestBed.createComponent(FuelTypeListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/fuel-types')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.editFuelType = { id: 1, name: '', description: '' };
    comp.submitEdit();
    expect(comp.editError).toBeTruthy();
  });

  it('submitEdit should PUT and reload on success', () => {
    const fixture = TestBed.createComponent(FuelTypeListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/fuel-types')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.editFuelType = { id: 3, name: 'Gasoil 50', description: '' };
    comp.submitEdit();

    const put = httpMock.expectOne(r => r.method === 'PUT' && r.url.includes('/fuel-types/3'));
    put.flush({ success: true, data: { id: 3, name: 'Gasoil 50', description: '', createdAt: '' } });

    httpMock.expectOne(r => r.method === 'GET' && r.url.includes('/fuel-types')).flush({ success: true, data: [] });
    expect(comp.showEditForm).toBeFalse();
  });

  it('deleteFuelType should DELETE and reload', () => {
    const fixture = TestBed.createComponent(FuelTypeListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/fuel-types')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.deleteFuelType(7);

    const del = httpMock.expectOne(r => r.method === 'DELETE' && r.url.includes('/fuel-types/7'));
    del.flush({ success: true });

    httpMock.expectOne(r => r.method === 'GET' && r.url.includes('/fuel-types')).flush({ success: true, data: [] });
    expect(comp.confirmingId).toBeNull();
  });
});
