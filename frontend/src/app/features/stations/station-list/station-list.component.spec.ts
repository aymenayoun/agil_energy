import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Observable, of } from 'rxjs';

import { StationListComponent } from './station-list.component';
import { StationService } from '../../../core/services/station.service';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ReportService } from '../../../core/services/report.service';

const mockStationService = {
  getAllStations: (): Observable<{ success: boolean; data: any[] }> =>
    of({ success: true, data: [] }),
  createStation: (): Observable<{ success: boolean }> =>
    of({ success: true }),
  updateStation: (): Observable<{ success: boolean; data: any }> =>
    of({ success: true, data: {} }),
  deactivateStation: (): Observable<any> => of({}),
  activateStation: (): Observable<any> => of({}),
  addTank: (): Observable<{ success: boolean }> => of({ success: true }),
};

const mockAuthService = {
  hasRole: (_role: string): boolean => false,
};

const mockNotifService = {
  success: (_msg: string): void => {},
  error: (_msg: string): void => {},
};

const mockReportService = {
  downloadMonthlyReport: (): Observable<any> => of(new Blob()),
  triggerBrowserDownload: (): void => {},
};

describe('StationListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StationListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: StationService, useValue: mockStationService },
        { provide: AuthService, useValue: mockAuthService },
        { provide: NotificationService, useValue: mockNotifService },
        { provide: ReportService, useValue: mockReportService },
      ]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should initialize with default values', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    const comp = fixture.componentInstance;
    expect(comp.stations).toEqual([]);
    expect(comp.activeView).toBe('list');
    expect(comp.isAdmin).toBeFalse();
    expect(comp.canExportReport).toBeFalse();
  });

  it('should load stations on init', () => {
    const spy = spyOn(mockStationService, 'getAllStations').and.returnValue(
      of({ success: true, data: [{ id: 1, name: 'Station A', region: 'Nord' }] }) as Observable<{ success: boolean; data: any[] }>
    );
    const fixture = TestBed.createComponent(StationListComponent);
    fixture.detectChanges();
    expect(spy).toHaveBeenCalled();
    expect(fixture.componentInstance.stations.length).toBe(1);
  });

  it('should filter displayedStations by searchStationId', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    const comp = fixture.componentInstance;
    comp.stations = [
      { id: 1, name: 'A', region: 'Nord' } as any,
      { id: 2, name: 'B', region: 'Sud' } as any,
    ];
    comp.searchStationId = 1;
    expect(comp.displayedStations.length).toBe(1);
    expect(comp.displayedStations[0].id).toBe(1);
  });

  it('should return all stations when searchStationId is null', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    const comp = fixture.componentInstance;
    comp.stations = [
      { id: 1, name: 'A', region: 'Nord' } as any,
      { id: 2, name: 'B', region: 'Sud' } as any,
    ];
    comp.searchStationId = null;
    expect(comp.displayedStations.length).toBe(2);
  });

  it('getOverallStockStatus should return "empty" when no tanks', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    const status = fixture.componentInstance.getOverallStockStatus({ tanks: [] } as any);
    expect(status).toBe('empty');
  });

  it('getOverallStockStatus should return "critical" when a tank is critical', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    const status = fixture.componentInstance.getOverallStockStatus({
      tanks: [{ critical: true, stockPercentage: 5 }]
    } as any);
    expect(status).toBe('critical');
  });

  it('getOverallStockStatus should return "warning" when stock < 40%', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    const status = fixture.componentInstance.getOverallStockStatus({
      tanks: [{ critical: false, stockPercentage: 30 }]
    } as any);
    expect(status).toBe('warning');
  });

  it('getOverallStockStatus should return "ok" when all tanks are healthy', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    const status = fixture.componentInstance.getOverallStockStatus({
      tanks: [{ critical: false, stockPercentage: 80 }]
    } as any);
    expect(status).toBe('ok');
  });

  it('should open and close report modal', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    const comp = fixture.componentInstance;
    const station = { id: 1, name: 'Station A' } as any;
    comp.openReportModal(station);
    expect(comp.showReportModal).toBeTrue();
    expect(comp.reportStation).toEqual(station);
    comp.closeReportModal();
    expect(comp.showReportModal).toBeFalse();
    expect(comp.reportStation).toBeNull();
  });

  it('should open and close edit station modal', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    const station = { id: 5, name: 'X', region: 'Sud', address: '123 rue', latitude: 1, longitude: 2 } as any;
    comp.openEditStation(station);
    expect(comp.showEditStation).toBeTrue();
    expect(comp.editStation.id).toBe(5);
    comp.closeEditStation();
    expect(comp.showEditStation).toBeFalse();
  });

  it('should show formError if createStation called with missing fields', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.newStation = { name: '', region: '' };
    comp.createStation();
    expect(comp.formError).toBeTruthy();
  });

  it('should switch view to map and back to list', () => {
    const fixture = TestBed.createComponent(StationListComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.switchView('map');
    expect(comp.activeView).toBe('map');
    comp.switchView('list');
    expect(comp.activeView).toBe('list');
  });
});
