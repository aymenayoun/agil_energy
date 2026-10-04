import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  // ── Token / state accessors ──

  it('isLoggedIn() should return false when no token', () => {
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('isLoggedIn() should return true when token exists', () => {
    localStorage.setItem('token', 'abc');
    expect(service.isLoggedIn()).toBeTrue();
  });

  it('getToken() should return stored token', () => {
    localStorage.setItem('token', 'jwt123');
    expect(service.getToken()).toBe('jwt123');
  });

  it('getRefreshToken() should return stored refresh token', () => {
    localStorage.setItem('refreshToken', 'ref456');
    expect(service.getRefreshToken()).toBe('ref456');
  });

  it('getCurrentUser() should return null when no stored user', () => {
    expect(service.getCurrentUser()).toBeNull();
  });

  it('getRole() should return null when no user', () => {
    expect(service.getRole()).toBeNull();
  });

  it('hasRole() should return false when no user', () => {
    expect(service.hasRole('ADMIN')).toBeFalse();
  });

  it('hasAnyRole() should return false when no user', () => {
    expect(service.hasAnyRole(['ADMIN', 'MANAGER'])).toBeFalse();
  });

  it('getStationId() should return null when no user', () => {
    expect(service.getStationId()).toBeNull();
  });

  it('getStationName() should return null when no user', () => {
    expect(service.getStationName()).toBeNull();
  });

  it('getRegion() should return null when no user', () => {
    expect(service.getRegion()).toBeNull();
  });

  it('isStationManager() should return false when no user', () => {
    expect(service.isStationManager()).toBeFalse();
  });

  it('isManager() should return false when no user', () => {
    expect(service.isManager()).toBeFalse();
  });

  it('isAdmin() should return false when no user', () => {
    expect(service.isAdmin()).toBeFalse();
  });

  // ── getDefaultRoute ──

  it('getDefaultRoute() should return /dashboard for ADMIN', () => {
    spyOn(service, 'isStationManager').and.returnValue(false);
    expect(service.getDefaultRoute()).toBe('/dashboard');
  });

  it('getDefaultRoute() should return /sales for STATION_MANAGER', () => {
    spyOn(service, 'isStationManager').and.returnValue(true);
    expect(service.getDefaultRoute()).toBe('/sales');
  });

  // ── Auth flows ──

  it('login() should POST to /auth/login', () => {
    service.login({ email: 'a@b.c', password: 'pass' }).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.c', password: 'pass' });
    req.flush({ success: false, data: null, message: 'bad' });
  });

  it('verifyOtp() should POST to /auth/verify-otp', () => {
    service.verifyOtp({ otpToken: 'tok', code: '123456' }).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/auth/verify-otp`);
    expect(req.request.method).toBe('POST');
    req.flush({ success: false, data: null });
  });

  it('refresh() should POST to /auth/refresh', () => {
    localStorage.setItem('refreshToken', 'ref');
    service.refresh().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'ref' });
    req.flush({ success: false, data: null });
  });

  // ── Password reset ──

  it('forgotPassword() should POST email to /auth/forgot-password', () => {
    service.forgotPassword('user@agil.tn').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/auth/forgot-password`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'user@agil.tn' });
    req.flush({ success: true, message: 'ok', data: null });
  });

  it('resetPassword() should POST token and new password to /auth/reset-password', () => {
    service.resetPassword('sel.verifier', 'NewPass123').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/auth/reset-password`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'sel.verifier', newPassword: 'NewPass123' });
    req.flush({ success: true, message: 'ok', data: null });
  });

  it('forgotPassword() should NOT persist any token (no session side-effects)', () => {
    service.forgotPassword('user@agil.tn').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/auth/forgot-password`);
    req.flush({ success: true, message: 'ok', data: null });
    expect(localStorage.getItem('token')).toBeNull();
  });

  // ── clearSession ──

  it('clearSession() should remove tokens and navigate to /login', () => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('refreshToken', 'y');
    localStorage.setItem('user', '{}');
    const navSpy = spyOn(router, 'navigate');

    service.clearSession();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(service.getCurrentUser()).toBeNull();
    expect(navSpy).toHaveBeenCalledWith(['/login']);
  });

  // ── persistIfAuthenticated (via login) ──

  it('login() should persist tokens when response has token and no OTP required', () => {
    service.login({ email: 'a@b.c', password: 'pass' }).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush({
      success: true,
      data: {
        token: 'jwt-new',
        refreshToken: 'ref-new',
        requiresOtp: false,
        role: 'ADMIN',
        name: 'Admin'
      }
    });

    expect(localStorage.getItem('token')).toBe('jwt-new');
    expect(localStorage.getItem('refreshToken')).toBe('ref-new');
    expect(service.getCurrentUser()?.role).toBe('ADMIN');
  });

  it('login() should NOT persist when requiresOtp is true', () => {
    service.login({ email: 'a@b.c', password: 'pass' }).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush({
      success: true,
      data: { requiresOtp: true, otpToken: 'otp-tok' }
    });

    expect(localStorage.getItem('token')).toBeNull();
  });
});
