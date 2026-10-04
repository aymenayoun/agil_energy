import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LayoutComponent } from './layout.component';

describe('LayoutComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LayoutComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(LayoutComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should have sidebarOpen default to false', () => {
    const fixture = TestBed.createComponent(LayoutComponent);
    expect(fixture.componentInstance.sidebarOpen()).toBeFalse();
  });

  it('should inject NotificationService', () => {
    const fixture = TestBed.createComponent(LayoutComponent);
    expect(fixture.componentInstance.notif).toBeTruthy();
  });
});
