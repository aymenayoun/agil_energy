import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RegionPredictionViewComponent } from './region-prediction-view.component';

describe('RegionPredictionViewComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegionPredictionViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('getRiskBadge should map levels', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    expect(comp.getRiskBadge('HIGH')).toBe('danger');
    expect(comp.getRiskBadge('MEDIUM')).toBe('warning');
    expect(comp.getRiskBadge('LOW')).toBe('success');
  });

  it('formatFeatureName should map known features', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    expect(comp.formatFeatureName('lag_1')).toBe('Vente J-1');
    expect(comp.formatFeatureName('temperature_moy')).toBe('Température');
    expect(comp.formatFeatureName('unknown_feat')).toBe('unknown_feat');
  });

  it('getModelEntries should return empty array when no iaResult', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    expect(fixture.componentInstance.getModelEntries()).toEqual([]);
  });

  it('getSelectedRegionInfo should return matching region', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.regions = [
      { region: 'Nord', station_count: 5, fuel_types: 'Diesel' },
      { region: 'Sud', station_count: 3, fuel_types: 'Essence' }
    ];
    comp.selectedRegion = 'Sud';
    const info = comp.getSelectedRegionInfo();
    expect(info?.station_count).toBe(3);
  });

  it('onRegionChange should clear state', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.selectedFuelTypeId = 1;
    comp.iaResult = { some: 'data' };
    comp.selectedRegion = null;
    comp.onRegionChange();
    expect(comp.fuelTypes).toEqual([]);
    expect(comp.selectedFuelTypeId).toBeNull();
    expect(comp.iaResult).toBeNull();
  });
  it('should default to simple view mode', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    expect(fixture.componentInstance.viewMode).toBe('simple');
  });

  it('getSimpleRiskLabel should map risk levels', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { risk_level: 'HIGH' };
    expect(comp.getSimpleRiskLabel()).toBe('Risque élevé');
    comp.iaResult = { risk_level: 'LOW' };
    expect(comp.getSimpleRiskLabel()).toBe('Situation normale');
    comp.iaResult = null;
    expect(comp.getSimpleRiskLabel()).toBe('Non évalué');
  });

  it('getSimpleRiskMessage should return a message per risk level', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { risk_level: 'MEDIUM' };
    expect(comp.getSimpleRiskMessage()).toContain('surveiller');
    comp.iaResult = null;
    expect(comp.getSimpleRiskMessage()).toContain('Générez');
  });

  it('getRegionTomorrow should return first forecast value', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { forecast_7_days: [800, 820, 790] };
    expect(comp.getRegionTomorrow()).toBe(800);
    comp.iaResult = { forecast_7_days: [] };
    expect(comp.getRegionTomorrow()).toBeNull();
    comp.iaResult = null;
    expect(comp.getRegionTomorrow()).toBeNull();
  });

  it('getRegionWeekTotal should sum the forecast', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { forecast_7_days: [100, 200, 300] };
    expect(comp.getRegionWeekTotal()).toBe(600);
    comp.iaResult = null;
    expect(comp.getRegionWeekTotal()).toBeNull();
  });

  it('onViewModeChange should do nothing when no iaResult', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = null;
    const spy = spyOn(comp, 'buildChart');
    comp.onViewModeChange();
    expect(spy).not.toHaveBeenCalled();
  });

  it('onViewModeChange should redraw chart when forecast exists', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { forecast_7_days: [100, 200] };
    const chartSpy = spyOn(comp, 'buildChart');
    comp.onViewModeChange();
    expect(chartSpy).toHaveBeenCalled();
  });

  it('onViewModeChange should build feature chart only in expert mode', () => {
    const fixture = TestBed.createComponent(RegionPredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { feature_importance: [{ feature: 'lag_1', importance: 0.5 }] };
    const fiSpy = spyOn(comp, 'buildFeatureImportanceChart');

    comp.viewMode = 'simple';
    comp.onViewModeChange();
    expect(fiSpy).not.toHaveBeenCalled();

    comp.viewMode = 'expert';
    comp.onViewModeChange();
    expect(fiSpy).toHaveBeenCalled();
  });
});
