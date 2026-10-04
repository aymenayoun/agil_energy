import { TestBed } from '@angular/core/testing';
import { ShapExplainerComponent } from './shap-explainer.component';
import { ShapExplanation } from '../../../core/services/prediction.service';

describe('ShapExplainerComponent', () => {
  const mockShap: ShapExplanation = {
    base_value: 1000,
    top_features_per_day: [[
      { feature: 'lag_1', value: 500, shap_value: 50 },
      { feature: 'rolling_mean_7', value: 450, shap_value: -20 }
    ]],
    global_top_features: [
      { feature: 'lag_1', mean_abs_shap: 45 },
      { feature: 'rolling_mean_7', mean_abs_shap: 30 }
    ]
  };
  const mockForecast = [1200, 1100, 1300, 900, 1050, 1150, 1250];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShapExplainerComponent]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ShapExplainerComponent);
    // Required signal inputs must be set BEFORE detectChanges()
    fixture.componentRef.setInput('shap', mockShap);
    fixture.componentRef.setInput('forecast', mockForecast);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });
});
