import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PredictionViewComponent } from './prediction-view.component';

describe('PredictionViewComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PredictionViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should default to predict tab', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    expect(fixture.componentInstance.activeTab).toBe('predict');
  });

  it('getRiskBadge should map risk levels', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    expect(comp.getRiskBadge('HIGH')).toBe('danger');
    expect(comp.getRiskBadge('MEDIUM')).toBe('warning');
    expect(comp.getRiskBadge('LOW')).toBe('success');
  });

  it('getMapeQuality should classify MAPE values', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    expect(comp.getMapeQuality(0.05)).toBe('success');
    expect(comp.getMapeQuality(0.15)).toBe('warning');
    expect(comp.getMapeQuality(0.30)).toBe('danger');
  });

  it('getMapeLabel should return text labels', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    expect(comp.getMapeLabel(0.05)).toBe('Excellent');
    expect(comp.getMapeLabel(0.15)).toBe('Acceptable');
    expect(comp.getMapeLabel(0.30)).toBe('Faible');
  });

  it('formatFeatureName should map known features', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    expect(comp.formatFeatureName('rolling_mean_7')).toBe('Moyenne 7j');
    expect(comp.formatFeatureName('is_ramadan')).toBe('Ramadan');
    expect(comp.formatFeatureName('is_weekend')).toBe('Weekend');
    expect(comp.formatFeatureName('fuel_price')).toBe('Prix carburant');
  });

  it('formatFeatureName should return raw name for unknown features', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    expect(fixture.componentInstance.formatFeatureName('xyz_custom')).toBe('xyz_custom');
  });

  it('getModelEntries should return empty array when no iaResult', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    expect(fixture.componentInstance.getModelEntries()).toEqual([]);
  });

  it('getModelEntries should parse models from iaResult', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = {
      models: {
        'XGBoost': { mae: 10, rmse: 15, mape: 0.05 },
        'Prophet': { mae: 12, rmse: 18, mape: 0.08 }
      }
    };
    const entries = comp.getModelEntries();
    expect(entries.length).toBe(2);
    expect(entries[0].name).toBe('XGBoost');
    expect(entries[0].mae).toBe(10);
  });

  it('onStationChange should reset selections', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.selectedFuelTypeId = 1;
    comp.iaResult = { some: 'data' };
    comp.predictions = [{ id: 1 } as any];
    comp.hasSearched = true;

    comp.stations = [{ id: 5, tanks: [{ fuelTypeId: 2, fuelTypeName: 'Diesel' }] } as any];
    comp.selectedStationId = 5;
    comp.onStationChange();

    expect(comp.selectedFuelTypeId).toBeNull();
    expect(comp.iaResult).toBeNull();
    expect(comp.predictions).toEqual([]);
    expect(comp.hasSearched).toBeFalse();
    expect(comp.fuelTypes.length).toBe(1);
  });

  it('getQuantileSummary should return null when no quantiles', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = null;
    expect(comp.getQuantileSummary()).toBeNull();
    comp.iaResult = {};
    expect(comp.getQuantileSummary()).toBeNull();
  });

  it('should default to simple view mode', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    expect(fixture.componentInstance.viewMode).toBe('simple');
  });

  it('getSimpleRiskLabel should map risk levels', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { risk_level: 'HIGH' };
    expect(comp.getSimpleRiskLabel()).toBe('Risque élevé');
    comp.iaResult = { risk_level: 'MEDIUM' };
    expect(comp.getSimpleRiskLabel()).toBe('Risque modéré');
    comp.iaResult = { risk_level: 'LOW' };
    expect(comp.getSimpleRiskLabel()).toBe('Situation normale');
    comp.iaResult = null;
    expect(comp.getSimpleRiskLabel()).toBe('Non évalué');
  });

  it('getSimpleRiskMessage should include rupture days when present', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { risk_level: 'HIGH', days_before_rupture: 2.4 };
    expect(comp.getSimpleRiskMessage()).toContain('2');
    comp.iaResult = { risk_level: 'LOW' };
    expect(comp.getSimpleRiskMessage()).toContain('Aucune action');
  });

  it('getTomorrowForecast should prefer predictions then P50 quantile', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.predictions = [{ predictedQuantity: 1200 } as any];
    expect(comp.getTomorrowForecast()).toBe(1200);
    comp.predictions = [];
    comp.iaResult = { quantiles: { P50: [950] } };
    expect(comp.getTomorrowForecast()).toBe(950);
    comp.iaResult = null;
    expect(comp.getTomorrowForecast()).toBeNull();
  });

  it('getWeekTotal should sum predicted quantities', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.predictions = [
      { predictedQuantity: 100 } as any,
      { predictedQuantity: 250 } as any
    ];
    expect(comp.getWeekTotal()).toBe(350);
    comp.predictions = [];
    expect(comp.getWeekTotal()).toBeNull();
  });

  it('onViewModeChange should do nothing when no iaResult', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = null;
    const spy = spyOn(comp, 'buildChart');
    comp.onViewModeChange();
    expect(spy).not.toHaveBeenCalled();
  });

  it('onViewModeChange should redraw chart when predictions exist', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { some: 'data' };
    comp.predictions = [{ predictedQuantity: 100 } as any];
    const chartSpy = spyOn(comp, 'buildChart');
    comp.onViewModeChange();
    expect(chartSpy).toHaveBeenCalled();
  });

  it('onViewModeChange should build feature chart only in expert mode', () => {
    const fixture = TestBed.createComponent(PredictionViewComponent);
    const comp = fixture.componentInstance;
    comp.iaResult = { feature_importance: [{ feature: 'lag_1', importance: 0.5 }] };
    comp.predictions = [];
    const fiSpy = spyOn(comp, 'buildFeatureImportanceChart');

    comp.viewMode = 'simple';
    comp.onViewModeChange();
    expect(fiSpy).not.toHaveBeenCalled();

    comp.viewMode = 'expert';
    comp.onViewModeChange();
    expect(fiSpy).toHaveBeenCalled();
  });
});
