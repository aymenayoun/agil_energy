import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('should add Authorization header when token exists', () => {
    spyOn(authService, 'getToken').and.returnValue('my-jwt-token');

    http.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-jwt-token');
    req.flush({});
  });

  it('should not add Authorization header when no token', () => {
    spyOn(authService, 'getToken').and.returnValue(null);

    http.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('should pass through non-401 errors', () => {
    spyOn(authService, 'getToken').and.returnValue('tok');

    let errorStatus = 0;
    http.get('/api/test').subscribe({
      error: (err) => { errorStatus = err.status; }
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Not found', { status: 404, statusText: 'Not Found' });

    expect(errorStatus).toBe(404);
  });

  it('should clear session on 401 when no refresh token', () => {
    spyOn(authService, 'getToken').and.returnValue('tok');
    spyOn(authService, 'getRefreshToken').and.returnValue(null);
    const clearSpy = spyOn(authService, 'clearSession');

    http.get('/api/test').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/test');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(clearSpy).toHaveBeenCalled();
  });
});
