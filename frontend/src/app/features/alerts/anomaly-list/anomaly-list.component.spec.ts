import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AnomalyListComponent } from './anomaly-list.component';

describe('AnomalyListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnomalyListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(AnomalyListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('getSeverityBadge should map correctly', () => {
    const fixture = TestBed.createComponent(AnomalyListComponent);
    const comp = fixture.componentInstance;
    expect(comp.getSeverityBadge('HIGH')).toBe('danger');
    expect(comp.getSeverityBadge('MEDIUM')).toBe('warning');
    expect(comp.getSeverityBadge('LOW')).toBe('info');
  });

  it('getSeverityLabel should map correctly', () => {
    const fixture = TestBed.createComponent(AnomalyListComponent);
    const comp = fixture.componentInstance;
    expect(comp.getSeverityLabel('HIGH')).toBe('Critique');
    expect(comp.getSeverityLabel('MEDIUM')).toBe('Moyenne');
    expect(comp.getSeverityLabel('LOW')).toBe('Faible');
  });

  it('getSeverityIcon should map correctly', () => {
    const fixture = TestBed.createComponent(AnomalyListComponent);
    const comp = fixture.componentInstance;
    expect(comp.getSeverityIcon('HIGH')).toBe('error');
    expect(comp.getSeverityIcon('MEDIUM')).toBe('warning');
    expect(comp.getSeverityIcon('LOW')).toBe('info');
  });

  it('absZ should return absolute value of zScore', () => {
    const fixture = TestBed.createComponent(AnomalyListComponent);
    const comp = fixture.componentInstance;
    expect(comp.absZ({ zScore: -3.5 } as any)).toBe(3.5);
    expect(comp.absZ({ zScore: 2.1 } as any)).toBe(2.1);
    expect(comp.absZ({ zScore: null } as any)).toBe(0);
  });

  it('onStationChange should reset page to 0', () => {
    const fixture = TestBed.createComponent(AnomalyListComponent);
    fixture.detectChanges();
    fixture.componentInstance.currentPage = 3;
    fixture.componentInstance.onStationChange();
    expect(fixture.componentInstance.currentPage).toBe(0);
  });

  it('severityFilter should default to ALL', () => {
    const fixture = TestBed.createComponent(AnomalyListComponent);
    expect(fixture.componentInstance.severityFilter).toBe('ALL');
  });
});
