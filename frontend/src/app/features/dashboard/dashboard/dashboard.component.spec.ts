import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should start with loading = true', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    expect(fixture.componentInstance.loading).toBeTrue();
  });

  it('should start with dashboard = null', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    expect(fixture.componentInstance.dashboard).toBeNull();
  });

  it('getDaysClass should return danger for <= 3 days', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    expect(fixture.componentInstance.getDaysClass(2)).toBe('badge badge-danger');
    expect(fixture.componentInstance.getDaysClass(3)).toBe('badge badge-danger');
  });

  it('getDaysClass should return warning for 4-7 days', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    expect(fixture.componentInstance.getDaysClass(5)).toBe('badge badge-warning');
    expect(fixture.componentInstance.getDaysClass(7)).toBe('badge badge-warning');
  });

  it('getDaysClass should return success for > 7 days', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    expect(fixture.componentInstance.getDaysClass(10)).toBe('badge badge-success');
  });

  it('getSeverityBadge should map severity levels', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const comp = fixture.componentInstance;
    expect(comp.getSeverityBadge('HIGH')).toBe('danger');
    expect(comp.getSeverityBadge('MEDIUM')).toBe('warning');
    expect(comp.getSeverityBadge('LOW')).toBe('info');
    expect(comp.getSeverityBadge('UNKNOWN')).toBe('neutral');
  });
});
