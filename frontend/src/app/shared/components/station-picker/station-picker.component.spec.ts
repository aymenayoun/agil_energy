import { TestBed } from '@angular/core/testing';
import { StationPickerComponent } from './station-picker.component';

describe('StationPickerComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StationPickerComponent]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should start with no selection', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    expect(comp.selectedId).toBeNull();
    expect(comp.query).toBe('');
    expect(comp.open).toBeFalse();
  });

  it('select() should set selectedId and emit', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;

    let emitted: number | null = undefined as any;
    comp.stationChange.subscribe((v: number | null) => emitted = v);

    const station = { id: 5, name: 'Station X', region: 'Nord' } as any;
    comp.select(station);

    expect(comp.selectedId).toBe(5);
    expect(comp.query).toBe('Station X');
    expect(comp.open).toBeFalse();
    expect(emitted).toBe(5);
  });

  it('select(null) should clear selection', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;

    let emitted: number | null = undefined as any;
    comp.stationChange.subscribe((v: number | null) => emitted = v);

    comp.select(null);

    expect(comp.selectedId).toBeNull();
    expect(comp.query).toBe('');
    expect(emitted).toBeNull();
  });

  it('clear() should reset and emit null', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    comp.selectedId = 3;
    comp.query = 'something';

    let emitted: number | null = undefined as any;
    comp.stationChange.subscribe((v: number | null) => emitted = v);

    comp.clear(new Event('click'));

    expect(comp.selectedId).toBeNull();
    expect(comp.query).toBe('');
    expect(emitted).toBeNull();
  });

  it('writeValue should sync selectedId and display', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    comp.stations = [{ id: 7, name: 'St-7', region: 'Sud' } as any];

    comp.writeValue(7);
    expect(comp.selectedId).toBe(7);
    expect(comp.query).toBe('St-7');
  });

  it('writeValue(null) should clear display', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    comp.writeValue(null);
    expect(comp.selectedId).toBeNull();
    expect(comp.query).toBe('');
  });

  it('onInput should open dropdown', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    comp.query = 'test';
    comp.onInput();
    expect(comp.open).toBeTrue();
    expect(comp.highlightedIndex).toBe(0);
  });

  it('onInput should clear selection when query is emptied', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    comp.selectedId = 5;
    comp.query = '';

    let emitted: number | null = undefined as any;
    comp.stationChange.subscribe((v: number | null) => emitted = v);

    comp.onInput();
    expect(comp.selectedId).toBeNull();
    expect(emitted).toBeNull();
  });

  it('highlight should wrap matching text in <mark>', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    comp.query = 'test';
    const result = comp.highlight('A test string');
    expect(result).toContain('<mark>test</mark>');
  });

  it('highlight should return empty string for null input', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    expect(fixture.componentInstance.highlight(null)).toBe('');
  });

  it('onFocus should not open when disabled', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    comp.disabled = true;
    comp.onFocus();
    expect(comp.open).toBeFalse();
  });

  it('filtered should contain all stations when query is empty', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    comp.stations = [
      { id: 1, name: 'A' } as any,
      { id: 2, name: 'B' } as any,
    ];
    comp.query = '';
    comp.onInput();
    expect(comp.filtered.length).toBe(2);
  });

  it('filtered should filter by name', () => {
    const fixture = TestBed.createComponent(StationPickerComponent);
    const comp = fixture.componentInstance;
    comp.stations = [
      { id: 1, name: 'Alpha', region: 'Nord' } as any,
      { id: 2, name: 'Beta', region: 'Sud' } as any,
    ];
    comp.query = 'alp';
    comp.onInput();
    expect(comp.filtered.length).toBe(1);
    expect(comp.filtered[0].name).toBe('Alpha');
  });
});
