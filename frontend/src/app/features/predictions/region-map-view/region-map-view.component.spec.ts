import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RegionMapViewComponent } from './region-map-view.component';

describe('RegionMapViewComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegionMapViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  // detectChanges() skipped: Leaflet needs a real #tunisia-map DOM element
  it('should create', () => {
    const fixture = TestBed.createComponent(RegionMapViewComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('getRiskBadge should map levels', () => {
    const fixture = TestBed.createComponent(RegionMapViewComponent);
    const comp = fixture.componentInstance;
    expect(comp.getRiskBadge('HIGH')).toBe('danger');
    expect(comp.getRiskBadge('MEDIUM')).toBe('warning');
    expect(comp.getRiskBadge('LOW')).toBe('success');
  });

  it('getForecastAverage should return 0 when no result', () => {
    const fixture = TestBed.createComponent(RegionMapViewComponent);
    const comp = fixture.componentInstance;
    comp.sideResult = null;
    expect(comp.getForecastAverage()).toBe(0);
  });

  it('getForecastAverage should compute average', () => {
    const fixture = TestBed.createComponent(RegionMapViewComponent);
    const comp = fixture.componentInstance;
    comp.sideResult = { forecast_7_days: [100, 200, 300] };
    expect(comp.getForecastAverage()).toBe(200);
  });

  it('getForecastTotal should compute sum', () => {
    const fixture = TestBed.createComponent(RegionMapViewComponent);
    const comp = fixture.componentInstance;
    comp.sideResult = { forecast_7_days: [100, 200, 300] };
    expect(comp.getForecastTotal()).toBe(600);
  });

  it('getForecastTotal should return 0 when no data', () => {
    const fixture = TestBed.createComponent(RegionMapViewComponent);
    const comp = fixture.componentInstance;
    comp.sideResult = null;
    expect(comp.getForecastTotal()).toBe(0);
  });

  it('closeSide should reset side panel state', () => {
    const fixture = TestBed.createComponent(RegionMapViewComponent);
    const comp = fixture.componentInstance;
    comp.sideOpen = true;
    comp.sideMode = 'region';
    comp.sideResult = { data: 'x' };
    comp.selectedEntityName = 'Nord';

    comp.closeSide();

    expect(comp.sideOpen).toBeFalse();
    expect(comp.sideMode).toBeNull();
    expect(comp.sideResult).toBeNull();
    expect(comp.selectedEntityName).toBe('');
  });
});
