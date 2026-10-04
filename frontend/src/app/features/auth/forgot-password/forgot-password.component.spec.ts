import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { ForgotPasswordComponent } from './forgot-password.component';
import { AuthService } from '../../../core/services/auth.service';

describe('ForgotPasswordComponent', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['forgotPassword']);

    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should start not submitted and not loading', () => {
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    const comp = fixture.componentInstance;
    expect(comp.submitted).toBeFalse();
    expect(comp.loading).toBeFalse();
  });

  it('onSubmit should call forgotPassword and show confirmation on success', () => {
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    const comp = fixture.componentInstance;
    authServiceSpy.forgotPassword.and.returnValue(
      of({ success: true, message: 'ok', timestamp: '', data: null as any })
    );

    comp.email = 'user@agil.tn';
    comp.onSubmit();

    expect(authServiceSpy.forgotPassword).toHaveBeenCalledWith('user@agil.tn');
    expect(comp.submitted).toBeTrue();
    expect(comp.loading).toBeFalse();
    expect(comp.errorMessage).toBe('');
  });

  it('onSubmit should still show confirmation for an unknown email (no enumeration)', () => {
    // The backend returns 200 regardless; the component must not reveal anything.
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    const comp = fixture.componentInstance;
    authServiceSpy.forgotPassword.and.returnValue(
      of({ success: true, message: 'ok', timestamp: '', data: null as any })
    );

    comp.email = 'ghost@agil.tn';
    comp.onSubmit();

    expect(comp.submitted).toBeTrue();
  });

  it('onSubmit should show a generic error on HTTP failure and not mark submitted', () => {
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    const comp = fixture.componentInstance;
    authServiceSpy.forgotPassword.and.returnValue(
      throwError(() => ({ error: { message: 'Server down' } }))
    );

    comp.email = 'user@agil.tn';
    comp.onSubmit();

    expect(comp.errorMessage).toBe('Server down');
    expect(comp.submitted).toBeFalse();
    expect(comp.loading).toBeFalse();
  });
});
