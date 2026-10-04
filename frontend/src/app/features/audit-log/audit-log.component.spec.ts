import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AuditLogComponent } from './audit-log.component';

describe('AuditLogComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditLogComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('getRoleBadge should map role classes', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    const comp = fixture.componentInstance;
    expect(comp.getRoleBadge('ADMIN')).toBe('badge badge-danger');
    expect(comp.getRoleBadge('MANAGER')).toBe('badge badge-info');
    expect(comp.getRoleBadge('STATION_MANAGER')).toBe('badge badge-neutral');
    expect(comp.getRoleBadge('SYSTEM')).toBe('badge badge-warning');
  });

  it('getActionIcon should map action prefixes', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    const comp = fixture.componentInstance;
    expect(comp.getActionIcon('CREATE_STATION')).toBe('add_circle');
    expect(comp.getActionIcon('VALIDATE_SALE')).toBe('check_circle');
    expect(comp.getActionIcon('DEACTIVATE_USER')).toBe('block');
    expect(comp.getActionIcon('ACTIVATE_STATION')).toBe('power_settings_new');
    expect(comp.getActionIcon('RESOLVE_ALERT')).toBe('done_all');
    expect(comp.getActionIcon('ADJUST_STOCK')).toBe('tune');
    expect(comp.getActionIcon('GENERATE_PREDICTION')).toBe('psychology');
    expect(comp.getActionIcon('UNKNOWN')).toBe('event');
  });

  it('getActionColor should map action prefixes to CSS vars', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    const comp = fixture.componentInstance;
    expect(comp.getActionColor('CREATE_STATION')).toBe('var(--green)');
    expect(comp.getActionColor('VALIDATE_SALE')).toBe('var(--blue)');
    expect(comp.getActionColor('DEACTIVATE_USER')).toBe('var(--red)');
    expect(comp.getActionColor('ACTIVATE_STATION')).toBe('var(--green)');
    expect(comp.getActionColor('UNKNOWN')).toBe('var(--text-muted)');
  });

  it('formatDetails should parse JSON string', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    const comp = fixture.componentInstance;
    const result = comp.formatDetails('{"stationId":1,"quantity":500}');
    expect(result).toContain('stationId: 1');
    expect(result).toContain('quantity: 500');
  });

  it('formatDetails should return raw string if not JSON', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    expect(fixture.componentInstance.formatDetails('plain text')).toBe('plain text');
  });

  it('formatDetails should return empty string for null', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    expect(fixture.componentInstance.formatDetails(null)).toBe('');
  });

  it('toggleDetails should toggle expandedId', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    const comp = fixture.componentInstance;
    comp.toggleDetails(5);
    expect(comp.expandedId).toBe(5);
    comp.toggleDetails(5);
    expect(comp.expandedId).toBeNull();
    comp.toggleDetails(3);
    expect(comp.expandedId).toBe(3);
  });

  it('applyFilters should filter by action and user', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    const comp = fixture.componentInstance;
    comp.logs = [
      { id: 1, action_label: 'Créer', utilisateur: 'Ali' } as any,
      { id: 2, action_label: 'Valider', utilisateur: 'Ali' } as any,
      { id: 3, action_label: 'Créer', utilisateur: 'Sara' } as any,
    ];
    comp.actionFilter = 'Créer';
    comp.userFilter = 'Ali';
    comp.applyFilters();
    expect(comp.filteredLogs.length).toBe(1);
    expect(comp.filteredLogs[0].id).toBe(1);
  });

  it('resetFilters should clear all filters', () => {
    const fixture = TestBed.createComponent(AuditLogComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.startDate = '2025-01-01';
    comp.endDate = '2025-01-31';
    comp.actionFilter = 'Créer';
    comp.userFilter = 'Ali';
    comp.resetFilters();
    expect(comp.startDate).toBe('');
    expect(comp.endDate).toBe('');
    expect(comp.actionFilter).toBe('');
    expect(comp.userFilter).toBe('');
  });
});
