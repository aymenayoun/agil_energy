import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, Observable } from 'rxjs';
import { DeliveryListComponent } from './delivery-list.component';
import { DeliveryService } from '../../../core/services/delivery.service';
import { StationService } from '../../../core/services/station.service';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';

const mockDeliveryService = {
  getDeliveries: (): Observable<any> => of({ success: true, data: [] }),
  validateDelivery: (): Observable<any> => of({ success: true, data: { id: 1, validated: true } }),
};

const mockStationService = {
  getAllStations: () => of({ success: true, data: [] }),
};

const mockAuthService = {
  getRole: () => 'ADMIN',
  getCurrentUser: () => null,
  isLoggedIn: () => true,
  getToken: () => 'tok',
  getRefreshToken: () => null,
};

const mockNotifService = {
  success: (_msg: string) => {},
  error: (_msg: string) => {},
  info: (_msg: string) => {},
};

describe('DeliveryListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeliveryListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DeliveryService, useValue: mockDeliveryService },
        { provide: StationService, useValue: mockStationService },
        { provide: AuthService, useValue: mockAuthService },
        { provide: NotificationService, useValue: mockNotifService },
      ]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(DeliveryListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should initialize with empty deliveries', () => {
    const fixture = TestBed.createComponent(DeliveryListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.deliveries).toEqual([]);
  });

  it('should set userRole on init', () => {
    const fixture = TestBed.createComponent(DeliveryListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.userRole).toBe('ADMIN');
  });

  it('canValidate should return true for ADMIN', () => {
    const fixture = TestBed.createComponent(DeliveryListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.canValidate()).toBeTrue();
  });

  it('canValidate should return false for STATION_MANAGER', () => {
    const fixture = TestBed.createComponent(DeliveryListComponent);
    fixture.componentInstance.userRole = 'STATION_MANAGER';
    expect(fixture.componentInstance.canValidate()).toBeFalse();
  });

  it('should compute totalVolume from deliveries', () => {
    const spy = spyOn(mockDeliveryService, 'getDeliveries').and.returnValue(
      of({ success: true, data: [{ quantity: 500 }, { quantity: 300 }] })
    );
    const fixture = TestBed.createComponent(DeliveryListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.totalVolume).toBe(800);
  });
});
