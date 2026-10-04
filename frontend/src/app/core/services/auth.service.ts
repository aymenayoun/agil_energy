import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  AuthResponse,
  LoginRequest,
  LogoutRequest,
  RefreshTokenRequest,
  VerifyOtpRequest
} from '../models';
import { WebSocketService } from './websocket.service';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private apiUrl = environment.apiUrl;
  private currentUserSubject = new BehaviorSubject<AuthResponse | null>(this.getStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();
  private readonly ws = inject(WebSocketService);

  constructor(private http: HttpClient, private router: Router) {
    if (this.getToken()) { this.ws.connect(); }
  }

  // ---------- Auth flows ----------

  login(request: LoginRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.apiUrl}/auth/login`, request)
      .pipe(tap(response => this.persistIfAuthenticated(response)));
  }

  verifyOtp(request: VerifyOtpRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.apiUrl}/auth/verify-otp`, request)
      .pipe(tap(response => this.persistIfAuthenticated(response)));
  }

  refresh(): Observable<ApiResponse<AuthResponse>> {
    const refreshToken = this.getRefreshToken();
    const body: RefreshTokenRequest = { refreshToken: refreshToken ?? '' };
    return this.http.post<ApiResponse<AuthResponse>>(`${this.apiUrl}/auth/refresh`, body)
      .pipe(tap(response => this.persistIfAuthenticated(response)));
  }

  /** Step 1 of password reset: request a reset link be emailed. */
  forgotPassword(email: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.apiUrl}/auth/forgot-password`, { email });
  }

  /** Step 2 of password reset: submit the emailed token and a new password. */
  resetPassword(token: string, newPassword: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.apiUrl}/auth/reset-password`, { token, newPassword });
  }

  logout(allDevices: boolean = false): void {
    const refreshToken = this.getRefreshToken();
    const accessToken = this.getToken();

    if (accessToken) {
      const body: LogoutRequest = { refreshToken: refreshToken ?? undefined, allDevices };
      this.http.post(`${this.apiUrl}/auth/logout`, body).subscribe({ next: () => {}, error: () => {} });
    }
    this.clearSession();
  }

  clearSession(): void {
    this.ws.disconnect();
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }

  // ---------- Token / scope accessors ----------

  getToken(): string | null { return localStorage.getItem('token'); }
  getRefreshToken(): string | null { return localStorage.getItem('refreshToken'); }
  isLoggedIn(): boolean { return !!this.getToken(); }
  getCurrentUser(): AuthResponse | null { return this.currentUserSubject.value; }
  getRole(): string | null { return this.getCurrentUser()?.role ?? null; }
  hasRole(role: string): boolean { return this.getRole() === role; }
  hasAnyRole(roles: string[]): boolean {
    const r = this.getRole();
    return r ? roles.includes(r) : false;
  }

  /** Station id the logged-in user is scoped to (STATION_MANAGER only), or null. */
  getStationId(): number | null { return this.getCurrentUser()?.stationId ?? null; }
  getStationName(): string | null { return this.getCurrentUser()?.stationName ?? null; }

  /** Region the logged-in user is scoped to (MANAGER only), or null. */
  getRegion(): string | null { return this.getCurrentUser()?.region ?? null; }

  isStationManager(): boolean { return this.hasRole('STATION_MANAGER'); }
  isManager(): boolean { return this.hasRole('MANAGER'); }
  isAdmin(): boolean { return this.hasRole('ADMIN'); }

  /** True when the user has no scope restriction (admin only now). */
  isUnscoped(): boolean { return this.isAdmin(); }

  /**
   * The default landing page for the current user after login.
   * ADMIN / MANAGER → dashboard; STATION_MANAGER → sales.
   */
  getDefaultRoute(): string {
    if (this.isStationManager()) return '/sales';
    return '/dashboard';
  }

  // ---------- Internals ----------

  private persistIfAuthenticated(response: ApiResponse<AuthResponse>): void {
    if (response.success && response.data && response.data.token && !response.data.requiresOtp) {
      localStorage.setItem('token', response.data.token);
      if (response.data.refreshToken) {
        localStorage.setItem('refreshToken', response.data.refreshToken);
      }
      localStorage.setItem('user', JSON.stringify(response.data));
      this.currentUserSubject.next(response.data);
      this.ws.connect();
    }
  }

  private getStoredUser(): AuthResponse | null {
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : null;
  }
}
