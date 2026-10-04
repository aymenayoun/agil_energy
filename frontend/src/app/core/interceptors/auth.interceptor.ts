import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpEvent } from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

// Module-level coordination state. Functional interceptors are singletons,
// so this is shared across all requests for the lifetime of the app.
let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

const AUTH_ENDPOINTS = ['/auth/login', '/auth/verify-otp', '/auth/refresh', '/auth/logout'];

const isAuthEndpoint = (url: string): boolean =>
  AUTH_ENDPOINTS.some(ep => url.includes(ep));

const addToken = (req: HttpRequest<unknown>, token: string): HttpRequest<unknown> =>
  req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  const authReq = token ? addToken(req, token) : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Only react to 401, and never try to refresh on auth endpoints themselves.
      if (error.status !== 401 || isAuthEndpoint(req.url)) {
        return throwError(() => error);
      }

      // No refresh token? Nothing we can do — force logout.
      if (!authService.getRefreshToken()) {
        authService.clearSession();
        return throwError(() => error);
      }

      return handle401(req, next, authService);
    })
  );
};

function handle401(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AuthService
): Observable<HttpEvent<unknown>> {
  if (!isRefreshing) {
    isRefreshing = true;
    refreshTokenSubject.next(null);

    return authService.refresh().pipe(
      switchMap(response => {
        isRefreshing = false;
        const newToken = response?.data?.token;
        if (!newToken) {
          authService.clearSession();
          return throwError(() => new Error('Refresh failed: no token in response'));
        }
        refreshTokenSubject.next(newToken);
        return next(addToken(req, newToken));
      }),
      catchError(err => {
        isRefreshing = false;
        refreshTokenSubject.next(null);
        authService.clearSession();
        return throwError(() => err);
      })
    );
  }

  // A refresh is already in flight. Wait until it emits a new token, then retry.
  return refreshTokenSubject.pipe(
    filter((t): t is string => t !== null),
    take(1),
    switchMap(newToken => next(addToken(req, newToken)))
  );
}
