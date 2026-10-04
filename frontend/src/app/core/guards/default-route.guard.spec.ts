import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter, CanActivateFn } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { defaultRouteGuard } from './default-route.guard';
import { AuthService } from '../services/auth.service';

describe('defaultRouteGuard', () => {
  const executeGuard: CanActivateFn = (...params) =>
    TestBed.runInInjectionContext(() => defaultRouteGuard(...params));

  let router: Router;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    router = TestBed.inject(Router);
    authService = TestBed.inject(AuthService);
  });

  it('should be defined', () => {
    expect(executeGuard).toBeTruthy();
  });

  it('should always return false (always redirects)', () => {
    spyOn(authService, 'getDefaultRoute').and.returnValue('/dashboard');
    spyOn(router, 'navigate');

    const result = TestBed.runInInjectionContext(() =>
      defaultRouteGuard({} as any, {} as any)
    );

    expect(result).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should redirect STATION_MANAGER to /sales', () => {
    spyOn(authService, 'getDefaultRoute').and.returnValue('/sales');
    spyOn(router, 'navigate');

    TestBed.runInInjectionContext(() =>
      defaultRouteGuard({} as any, {} as any)
    );

    expect(router.navigate).toHaveBeenCalledWith(['/sales']);
  });
});
