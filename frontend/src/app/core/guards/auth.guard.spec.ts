import { TestBed } from '@angular/core/testing';
import { CanActivateFn, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authService: AuthService;
  let router: Router;

  const executeGuard: CanActivateFn = (...params) =>
    TestBed.runInInjectionContext(() => authGuard(...params));

  function makeRoute(roles?: string[]): ActivatedRouteSnapshot {
    return { data: roles ? { roles } : {} } as any;
  }

  const mockState = {} as RouterStateSnapshot;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });

  it('should redirect to /login when not logged in', () => {
    spyOn(authService, 'isLoggedIn').and.returnValue(false);
    const navSpy = spyOn(router, 'navigate');

    const result = TestBed.runInInjectionContext(() =>
      authGuard(makeRoute(), mockState)
    );

    expect(result).toBeFalse();
    expect(navSpy).toHaveBeenCalledWith(['/login']);
  });

  it('should allow access when logged in and no role restriction', () => {
    spyOn(authService, 'isLoggedIn').and.returnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard(makeRoute(), mockState)
    );

    expect(result).toBeTrue();
  });

  it('should allow access when user has required role', () => {
    spyOn(authService, 'isLoggedIn').and.returnValue(true);
    spyOn(authService, 'hasAnyRole').and.returnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard(makeRoute(['ADMIN', 'MANAGER']), mockState)
    );

    expect(result).toBeTrue();
  });

  it('should redirect to default route when user lacks required role', () => {
    spyOn(authService, 'isLoggedIn').and.returnValue(true);
    spyOn(authService, 'hasAnyRole').and.returnValue(false);
    spyOn(authService, 'getDefaultRoute').and.returnValue('/sales');
    const navSpy = spyOn(router, 'navigate');

    const result = TestBed.runInInjectionContext(() =>
      authGuard(makeRoute(['ADMIN']), mockState)
    );

    expect(result).toBeFalse();
    expect(navSpy).toHaveBeenCalledWith(['/sales']);
  });
});
