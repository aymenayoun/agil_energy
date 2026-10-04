import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { UserListComponent } from './user-list.component';
import { AuthService } from '../../../core/services/auth.service';
import { StationService } from '../../../core/services/station.service';
import { NotificationService } from '../../../core/services/notification.service';

const mockAuthService = {
  getCurrentUser: () => ({ userId: 1, name: 'Admin', role: 'ADMIN' }),
  getToken: () => 'tok',
  getRefreshToken: () => null,
  isLoggedIn: () => true,
};

const mockStationService = {
  getAllStations: () => of({ success: true, data: [] }),
};

const mockNotifService = {
  success: (_msg: string) => {},
  error: (_msg: string) => {},
  info: (_msg: string) => {},
};

describe('UserListComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: mockAuthService },
        { provide: StationService, useValue: mockStationService },
        { provide: NotificationService, useValue: mockNotifService },
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();

    // The component calls GET /users on init
    const req = httpMock.expectOne(r => r.url.includes('/users'));
    req.flush({ success: true, data: [] });

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should default newUser roleName to STATION_MANAGER', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    expect(fixture.componentInstance.newUser.roleName).toBe('STATION_MANAGER');
  });

  it('getRoleBadge should return correct class', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    const comp = fixture.componentInstance;
    expect(comp.getRoleBadge('ADMIN')).toBe('badge badge-danger');
    expect(comp.getRoleBadge('MANAGER')).toBe('badge badge-info');
    expect(comp.getRoleBadge('STATION_MANAGER')).toBe('badge badge-neutral');
  });

  it('getRoleLabel should return correct label', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    const comp = fixture.componentInstance;
    expect(comp.getRoleLabel('ADMIN')).toBe('Administrateur');
    expect(comp.getRoleLabel('MANAGER')).toBe('Gestionnaire Régional');
    expect(comp.getRoleLabel('STATION_MANAGER')).toBe('Resp. Station');
  });

  it('getScopeLabel should return stationName if present', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    const comp = fixture.componentInstance;
    const user = { stationName: 'Station X', region: 'Nord' } as any;
    expect(comp.getScopeLabel(user)).toBe('Station X');
  });

  it('getScopeLabel should return region if no stationName', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    const comp = fixture.componentInstance;
    const user = { stationName: null, region: 'Sud' } as any;
    expect(comp.getScopeLabel(user)).toBe('Sud');
  });

  it('should show formError if createUser called with missing fields', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/users')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.newUser = { name: '', email: '', password: '', roleName: 'ADMIN', stationId: null, region: null };
    comp.createUser();
    expect(comp.formError).toBeTruthy();
  });

  it('should show formError if password is too short', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/users')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.newUser = { name: 'Test', email: 'a@b.c', password: '123', roleName: 'ADMIN', stationId: null, region: null };
    comp.createUser();
    expect(comp.formError).toContain('8 caractères');
  });

  it('should require stationId for STATION_MANAGER', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/users')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.newUser = { name: 'Test', email: 'a@b.c', password: '12345678', roleName: 'STATION_MANAGER', stationId: null, region: null };
    comp.createUser();
    expect(comp.formError).toContain('station');
  });

  it('should require region for MANAGER', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/users')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    comp.newUser = { name: 'Test', email: 'a@b.c', password: '12345678', roleName: 'MANAGER', stationId: null, region: null };
    comp.createUser();
    expect(comp.formError).toContain('région');
  });

  it('should open and close edit form', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    httpMock.expectOne(r => r.url.includes('/users')).flush({ success: true, data: [] });

    const comp = fixture.componentInstance;
    const user = { id: 5, name: 'Ali', email: 'ali@agil.tn', roleName: 'ADMIN', status: 'ACTIVE', stationId: null, stationName: null, region: null, createdAt: '' };
    comp.openEdit(user);
    expect(comp.showEditForm).toBeTrue();
    expect(comp.editUser.id).toBe(5);
    comp.closeEdit();
    expect(comp.showEditForm).toBeFalse();
  });
});
