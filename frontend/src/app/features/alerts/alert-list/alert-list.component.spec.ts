import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AlertListComponent } from './alert-list.component';

describe('AlertListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AlertListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(AlertListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should start on page 0', () => {
    const fixture = TestBed.createComponent(AlertListComponent);
    expect(fixture.componentInstance.currentPage).toBe(0);
  });

  it('getSeverityBadge should map correctly', () => {
    const fixture = TestBed.createComponent(AlertListComponent);
    const comp = fixture.componentInstance;
    expect(comp.getSeverityBadge('HIGH')).toBe('danger');
    expect(comp.getSeverityBadge('MEDIUM')).toBe('warning');
    expect(comp.getSeverityBadge('LOW')).toBe('info');
  });

  it('getSeverityLabel should map correctly', () => {
    const fixture = TestBed.createComponent(AlertListComponent);
    const comp = fixture.componentInstance;
    expect(comp.getSeverityLabel('HIGH')).toBe('Critique');
    expect(comp.getSeverityLabel('MEDIUM')).toBe('Moyenne');
    expect(comp.getSeverityLabel('LOW')).toBe('Faible');
  });

  it('getTypeLabel should map alert types', () => {
    const fixture = TestBed.createComponent(AlertListComponent);
    const comp = fixture.componentInstance;
    expect(comp.getTypeLabel('STOCK_RUPTURE')).toBe('Rupture de stock');
    expect(comp.getTypeLabel('ANOMALY')).toBe('Anomalie');
    expect(comp.getTypeLabel('STOCK_INCOHERENT')).toBe('Stock incohérent');
    expect(comp.getTypeLabel('MISSING_ENTRY')).toBe('Saisie manquante');
    expect(comp.getTypeLabel('OTHER')).toBe('OTHER');
  });

  it('goToPage should update currentPage', () => {
    const fixture = TestBed.createComponent(AlertListComponent);
    fixture.detectChanges();
    fixture.componentInstance.goToPage(3);
    expect(fixture.componentInstance.currentPage).toBe(3);
  });

  it('onStationChange should reset page to 0', () => {
    const fixture = TestBed.createComponent(AlertListComponent);
    fixture.detectChanges();
    fixture.componentInstance.currentPage = 5;
    fixture.componentInstance.onStationChange();
    expect(fixture.componentInstance.currentPage).toBe(0);
  });
});
