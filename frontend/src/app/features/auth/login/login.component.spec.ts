import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError, timestamp } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/services/auth.service';

describe('LoginComponent', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'isLoggedIn', 'login', 'verifyOtp', 'getCurrentUser'
    ]);
    authServiceSpy.isLoggedIn.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should start on credentials step', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    expect(fixture.componentInstance.step).toBe('credentials');
  });

  it('should default loading to false', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    expect(fixture.componentInstance.loading).toBeFalse();
  });

  it('should default showPassword to false', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    expect(fixture.componentInstance.showPassword).toBeFalse();
  });

  it('should switch to OTP step when requiresOtp', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const comp = fixture.componentInstance;

    authServiceSpy.login.and.returnValue(of({
      success: true,
      message: '',
      timestamp: '',
      data: { requiresOtp: true, otpToken: 'otp-tok', otpExpiresInSeconds: 300 } as any
    }));

    comp.credentials = { email: 'a@b.c', password: 'pass' };
    comp.onLogin();

    expect(comp.step).toBe('otp');
    expect(comp.otpToken).toBe('otp-tok');
    expect(comp.loading).toBeFalse();
  });

  it('should show errorMessage on login failure response', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const comp = fixture.componentInstance;

    authServiceSpy.login.and.returnValue(of({
      success: false,
      message: 'Bad credentials',
      timestamp: '',
      data: null as any
    }));

    comp.onLogin();

    expect(comp.errorMessage).toBe('Bad credentials');
    expect(comp.loading).toBeFalse();
  });

  it('should show errorMessage on login HTTP error', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const comp = fixture.componentInstance;

    authServiceSpy.login.and.returnValue(
      throwError(() => ({ error: { message: 'Network error' } }))
    );

    comp.onLogin();

    expect(comp.errorMessage).toBe('Network error');
    expect(comp.loading).toBeFalse();
  });

  it('backToCredentials should reset OTP state', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const comp = fixture.componentInstance;
    comp.step = 'otp';
    comp.otpCode = '123456';
    comp.otpToken = 'tok';
    comp.errorMessage = 'some error';

    comp.backToCredentials();

    expect(comp.step).toBe('credentials');
    expect(comp.otpCode).toBe('');
    expect(comp.otpToken).toBeNull();
    expect(comp.errorMessage).toBe('');
  });

  it('onVerifyOtp should do nothing when otpToken is null', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const comp = fixture.componentInstance;
    comp.otpToken = null;
    comp.onVerifyOtp();
    expect(authServiceSpy.verifyOtp).not.toHaveBeenCalled();
  });
});
