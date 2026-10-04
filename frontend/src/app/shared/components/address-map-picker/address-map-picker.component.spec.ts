import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SimpleChange } from '@angular/core';
import { AddressMapPickerComponent, LocationResult } from './address-map-picker.component';

import * as L from 'leaflet';

// ── Leaflet mock ──────────────────────────────────────────────────────────────
//
// The component does: this.marker = L.marker(...).addTo(this.map)
// So `addTo` must return the *same* object that we later call `.setLatLng` /
// `.on` / `.getLatLng` / `.remove` on.  Making the mock self-referential
// (addTo returns itself) solves the shape-mismatch problem.
//
// We build the objects with plain jasmine.createSpy calls so we can call
// .calls.reset() between tests without re-running spyOn (which would throw
// "already spied on" on the second test run inside the same describe block).

const mockMarker: any = {
  addTo:     jasmine.createSpy('marker.addTo'),
  setLatLng: jasmine.createSpy('marker.setLatLng'),
  remove:    jasmine.createSpy('marker.remove'),
  getLatLng: jasmine.createSpy('marker.getLatLng').and.returnValue({ lat: 36.8, lng: 10.1 }),
  on:        jasmine.createSpy('marker.on'),
};
// Self-referential: addTo(map) returns the marker itself so the component
// stores the right object when it does `this.marker = L.marker(...).addTo(...)`.
mockMarker.addTo.and.returnValue(mockMarker);

const mockMap: any = {
  // setView / flyTo are chainable in real Leaflet; return mockMap for safety.
  setView:  jasmine.createSpy('map.setView').and.returnValue(undefined),
  flyTo:    jasmine.createSpy('map.flyTo').and.returnValue(undefined),
  on:       jasmine.createSpy('map.on'),
  remove:   jasmine.createSpy('map.remove'),
  addLayer: jasmine.createSpy('map.addLayer'),
};

const mockTileLayer: any = {
  addTo: jasmine.createSpy('tileLayer.addTo').and.returnValue({}),
};

// ── Helper: reset all spy call-counts between tests ──────────────────────────
function resetMocks(): void {
  [
    mockMarker.addTo, mockMarker.setLatLng, mockMarker.remove,
    mockMarker.getLatLng, mockMarker.on,
    mockMap.setView, mockMap.flyTo, mockMap.on, mockMap.remove, mockMap.addLayer,
    mockTileLayer.addTo,
  ].forEach(spy => spy.calls.reset());

  // Restore self-referential return values that .calls.reset() clears.
  mockMarker.addTo.and.returnValue(mockMarker);
  mockMarker.getLatLng.and.returnValue({ lat: 36.8, lng: 10.1 });
  mockMap.setView.and.returnValue(undefined);
  mockMap.flyTo.and.returnValue(undefined);
  mockTileLayer.addTo.and.returnValue({});
}

describe('AddressMapPickerComponent', () => {
  let httpMock: HttpTestingController;

  beforeAll(() => {
    // Spy on Leaflet *once* for the entire suite.
    // spyOn throws if called twice on the same function, so we must not put
    // these inside beforeEach.
    spyOn(L, 'map').and.returnValue(mockMap);
    spyOn(L, 'tileLayer').and.returnValue(mockTileLayer);
    spyOn(L, 'marker').and.returnValue(mockMarker);

    // The component does `delete (L.Icon.Default.prototype as any)._getIconUrl`
    // which is fine; we just stub mergeOptions so it doesn't throw.
    spyOn(L.Icon.Default, 'mergeOptions').and.stub();
  });

  beforeEach(async () => {
    resetMocks();

    await TestBed.configureTestingModule({
      imports: [AddressMapPickerComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Verify no unexpected HTTP requests are pending.
    httpMock.verify();
  });

  // ── Creation ────────────────────────────────────────────────────────────────

  it('should create', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should initialise with default values', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    // Read state *before* detectChanges to inspect the class defaults, not
    // the post-ngAfterViewInit state (which kicks off initMap via setTimeout).
    const comp = fixture.componentInstance;
    expect(comp.query).toBe('');
    expect(comp.suggestions).toEqual([]);
    expect(comp.showSuggestions).toBeFalse();
    expect(comp.searching).toBeFalse();
  });

  it('should reflect @Input address binding', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    fixture.componentInstance.address = '10 Rue de la Paix, Tunis';
    fixture.detectChanges();
    expect(fixture.componentInstance.address).toBe('10 Rue de la Paix, Tunis');
  });

  // ── Search input ─────────────────────────────────────────────────────────────

  it('should push value to searchSubject on onSearchInput without throwing', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    comp.query = 'Tunis';
    expect(() => comp.onSearchInput()).not.toThrow();
  });

  it('should show suggestions on focus when suggestions exist', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    comp.suggestions = [{ display_name: 'Tunis', lat: '36.8', lon: '10.1' }] as any;
    comp.showSuggestions = false;
    comp.onSearchFocus();
    expect(comp.showSuggestions).toBeTrue();
  });

  it('should not show suggestions on focus when list is empty', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    comp.suggestions = [];
    comp.onSearchFocus();
    expect(comp.showSuggestions).toBeFalse();
  });

  // ── clearSearch ──────────────────────────────────────────────────────────────

  it('should clear query, suggestions and emit on clearSearch', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    comp.query = 'Sfax';
    comp.suggestions = [{ display_name: 'Sfax', lat: '34.7', lon: '10.7' }] as any;
    comp.showSuggestions = true;

    let emitted!: LocationResult;
    comp.locationSelected.subscribe((v: LocationResult) => (emitted = v));

    comp.clearSearch();

    expect(comp.query).toBe('');
    expect(comp.suggestions).toEqual([]);
    expect(comp.showSuggestions).toBeFalse();
    expect(emitted).toEqual({ address: '', latitude: 0, longitude: 0 });
  });

  // ── selectSuggestion ─────────────────────────────────────────────────────────

  it('should emit locationSelected and update query on selectSuggestion', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    const result = {
      display_name: 'Avenue Habib Bourguiba, Tunis',
      lat: '36.819',
      lon: '10.165',
    };

    let emitted!: LocationResult;
    comp.locationSelected.subscribe((v: LocationResult) => (emitted = v));

    comp.selectSuggestion(result as any);

    expect(comp.query).toBe('Avenue Habib Bourguiba, Tunis');
    expect(comp.showSuggestions).toBeFalse();
    expect(emitted).toEqual({
      address: 'Avenue Habib Bourguiba, Tunis',
      latitude: 36.819,
      longitude: 10.165,
    });
  });

  // ── ngOnChanges ──────────────────────────────────────────────────────────────

  it('should sync query when address input changes after init', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    (comp as any).initialized = true;

    // Angular updates the @Input property before calling ngOnChanges
    comp.address = 'New Address';
    comp.ngOnChanges({
      address: new SimpleChange('old', 'New Address', false),
    });

    expect(comp.query).toBe('New Address');
  });

  it('should not sync query on first address change (firstChange = true)', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    (comp as any).initialized = true;
    comp.query = 'untouched';

    comp.ngOnChanges({
      address: new SimpleChange(null, 'New Address', true), // firstChange
    });

    expect(comp.query).toBe('untouched');
  });

  it('should do nothing in ngOnChanges when not yet initialized', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    // initialized is false by default — do NOT set it.
    comp.query = 'original';

    comp.ngOnChanges({
      address: new SimpleChange(null, 'Something', false),
    });

    expect(comp.query).toBe('original');
  });

  // ── onDocClick ───────────────────────────────────────────────────────────────

  it('should hide suggestions when clicking outside the wrapper', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    comp.showSuggestions = true;

    const outsideEl = document.createElement('div');
    document.body.appendChild(outsideEl);
    comp.onDocClick({ target: outsideEl } as unknown as Event);

    expect(comp.showSuggestions).toBeFalse();
    document.body.removeChild(outsideEl);
  });

  it('should keep suggestions visible when clicking inside the wrapper', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    comp.showSuggestions = true;

    // Build a DOM tree that matches `.address-picker-wrapper > input`
    const wrapper = document.createElement('div');
    wrapper.className = 'address-picker-wrapper';
    const inner = document.createElement('input');
    wrapper.appendChild(inner);
    document.body.appendChild(wrapper);

    comp.onDocClick({ target: inner } as unknown as Event);

    expect(comp.showSuggestions).toBeTrue();
    document.body.removeChild(wrapper);
  });

  // ── ngOnDestroy ──────────────────────────────────────────────────────────────

  it('should complete searchSubject on destroy', () => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    const spy = spyOn((comp as any).searchSubject, 'complete').and.callThrough();
    comp.ngOnDestroy();
    expect(spy).toHaveBeenCalled();
  });

  // ── Debounced HTTP search ────────────────────────────────────────────────────

  it('should call Nominatim search API after debounce', fakeAsync(() => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    comp.query = 'Carthage';
    comp.onSearchInput();
    tick(400); // advance past the 400 ms debounce

    const req = httpMock.expectOne(r =>
      r.url.includes('nominatim.openstreetmap.org/search'),
    );
    expect(req.request.params.get('q')).toBe('Carthage');

    req.flush([{ display_name: 'Carthage, Tunis', lat: '36.85', lon: '10.32' }]);

    expect(comp.suggestions.length).toBe(1);
    expect(comp.showSuggestions).toBeTrue();
    expect(comp.searching).toBeFalse();
  }));

  it('should not call API when query is shorter than 3 characters', fakeAsync(() => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    comp.query = 'Tu';
    comp.onSearchInput();
    tick(400);

    httpMock.expectNone(r => r.url.includes('nominatim'));
    expect(comp.suggestions).toEqual([]);
  }));

  it('should handle search API error gracefully', fakeAsync(() => {
    const fixture = TestBed.createComponent(AddressMapPickerComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    comp.query = 'Bizerte';
    comp.onSearchInput();
    tick(400);

    const req = httpMock.expectOne(r =>
      r.url.includes('nominatim.openstreetmap.org/search'),
    );
    req.error(new ProgressEvent('Network error'));

    expect(comp.suggestions).toEqual([]);
    expect(comp.searching).toBeFalse();
  }));
});
