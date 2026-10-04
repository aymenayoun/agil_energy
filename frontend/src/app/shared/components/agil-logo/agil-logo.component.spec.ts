import { TestBed } from '@angular/core/testing';
import { AgilLogoComponent } from './agil-logo.component';

describe('AgilLogoComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgilLogoComponent]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(AgilLogoComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should default to the "mark" variant', () => {
    const fixture = TestBed.createComponent(AgilLogoComponent);
    fixture.detectChanges();
    const svg: SVGElement | null = fixture.nativeElement.querySelector('svg.agil-mark');
    expect(svg).toBeTruthy();
  });

  it('should render the tile variant with a gold background rect', () => {
    const fixture = TestBed.createComponent(AgilLogoComponent);
    fixture.componentInstance.variant = 'tile';
    fixture.detectChanges();
    const svg: SVGElement | null = fixture.nativeElement.querySelector('svg.agil-tile');
    expect(svg).toBeTruthy();
    expect(svg!.querySelector('rect')).toBeTruthy();
  });

  it('should render the lockup variant with the wordmark text', () => {
    const fixture = TestBed.createComponent(AgilLogoComponent);
    fixture.componentInstance.variant = 'lockup';
    fixture.detectChanges();
    const svg: SVGElement | null = fixture.nativeElement.querySelector('svg.agil-lockup');
    expect(svg).toBeTruthy();
    expect(svg!.textContent).toContain('Agil');
    expect(svg!.textContent).toContain('energy');
  });

  it('should apply the size input to the mark width', () => {
    const fixture = TestBed.createComponent(AgilLogoComponent);
    fixture.componentInstance.variant = 'mark';
    fixture.componentInstance.size = 64;
    fixture.detectChanges();
    const svg: SVGElement | null = fixture.nativeElement.querySelector('svg.agil-mark');
    expect(svg!.getAttribute('width')).toBe('64');
  });

  it('should apply the width input to the lockup width', () => {
    const fixture = TestBed.createComponent(AgilLogoComponent);
    fixture.componentInstance.variant = 'lockup';
    fixture.componentInstance.width = 320;
    fixture.detectChanges();
    const svg: SVGElement | null = fixture.nativeElement.querySelector('svg.agil-lockup');
    expect(svg!.getAttribute('width')).toBe('320');
  });

  it('should set the aria-label from the label input', () => {
    const fixture = TestBed.createComponent(AgilLogoComponent);
    fixture.componentInstance.label = 'Custom label';
    fixture.detectChanges();
    const svg: SVGElement | null = fixture.nativeElement.querySelector('svg');
    expect(svg!.getAttribute('aria-label')).toBe('Custom label');
  });
});
