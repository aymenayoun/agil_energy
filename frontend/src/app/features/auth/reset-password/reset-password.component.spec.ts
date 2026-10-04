import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { ResetPasswordComponent } from './reset-password.component';
import { AuthService } from '../../../core/services/auth.service';

/** Build a component fixture with a given ?token= query param. */
function setup(token: string | null) {
  const authServiceSpy = jasmine.createSpyObj('AuthService', ['resetPassword']);
  const routeStub = {
    snapshot: {
      queryParamMap: convertToParamMap(token === null ? {} : { token })
    }
  } as unknown as ActivatedRoute;

  TestBed.configureTestingModule({
    imports: [ResetPasswordComponent],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: authServiceSpy },
      { provide: ActivatedRoute, useValue: routeStub }
    ]
  });

  const fixture = TestBed.createComponent(ResetPasswordComponent);
  return { fixture, comp: fixture.componentInstance, authServiceSpy };
}

describe('ResetPasswordComponent', () => {

  it('should create', () => {
    const { fixture } = setup('sel.verifier');
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should read the token from the query string', () => {
    const { fixture, comp } = setup('sel.verifier');
    fixture.detectChanges();
    expect(comp.token).toBe('sel.verifier');
    expect(comp.tokenMissing).toBeFalse();
  });

  it('should flag tokenMissing when no token is present', () => {
    const { fixture, comp } = setup(null);
    fixture.detectChanges();
    expect(comp.tokenMissing).toBeTrue();
  });

  // ── Password policy getters (mirror backend rules) ──

  it('should validate password policy correctly', () => {
    const { fixture, comp } = setup('sel.verifier');
    fixture.detectChanges();

    comp.password = 'short';
    expect(comp.hasMinLength).toBeFalse();

    comp.password = 'alllowercase1';
    expect(comp.hasUpper).toBeFalse();

    comp.password = 'ALLUPPERCASE1';
    expect(comp.hasLower).toBeFalse();

    comp.password = 'NoDigitsHere';
    expect(comp.hasDigit).toBeFalse();

    comp.password = 'ValidPass123';
    expect(comp.hasMinLength).toBeTrue();
    expect(comp.hasUpper).toBeTrue();
    expect(comp.hasLower).toBeTrue();
    expect(comp.hasDigit).toBeTrue();
  });

  it('passwordsMatch should be true only when both fields are equal and non-empty', () => {
    const { fixture, comp } = setup('sel.verifier');
    fixture.detectChanges();

    comp.password = 'ValidPass123';
    comp.confirmPassword = '';
    expect(comp.passwordsMatch).toBeFalse();

    comp.confirmPassword = 'Different1';
    expect(comp.passwordsMatch).toBeFalse();

    comp.confirmPassword = 'ValidPass123';
    expect(comp.passwordsMatch).toBeTrue();
  });

  it('isValid should require a strong, matching password', () => {
    const { fixture, comp } = setup('sel.verifier');
    fixture.detectChanges();

    comp.password = 'weak';
    comp.confirmPassword = 'weak';
    expect(comp.isValid).toBeFalse();

    comp.password = 'ValidPass123';
    comp.confirmPassword = 'ValidPass123';
    expect(comp.isValid).toBeTrue();
  });

  // ── onSubmit ──

  it('onSubmit should do nothing when password is invalid', () => {
    const { fixture, comp, authServiceSpy } = setup('sel.verifier');
    fixture.detectChanges();
    comp.password = 'weak';
    comp.confirmPassword = 'weak';

    comp.onSubmit();

    expect(authServiceSpy.resetPassword).not.toHaveBeenCalled();
  });

  it('onSubmit should call resetPassword and set success on a valid submit', () => {
    const { fixture, comp, authServiceSpy } = setup('sel.verifier');
    authServiceSpy.resetPassword.and.returnValue(
      of({ success: true, message: 'ok', timestamp: '', data: null as any })
    );
    fixture.detectChanges();

    comp.password = 'ValidPass123';
    comp.confirmPassword = 'ValidPass123';
    comp.onSubmit();

    expect(authServiceSpy.resetPassword).toHaveBeenCalledWith('sel.verifier', 'ValidPass123');
    expect(comp.success).toBeTrue();
    expect(comp.loading).toBeFalse();
  });

  it('onSubmit should surface a backend error (e.g. expired token)', () => {
    const { fixture, comp, authServiceSpy } = setup('sel.verifier');
    authServiceSpy.resetPassword.and.returnValue(
      throwError(() => ({ error: { message: 'Lien expiré' } }))
    );
    fixture.detectChanges();

    comp.password = 'ValidPass123';
    comp.confirmPassword = 'ValidPass123';
    comp.onSubmit();

    expect(comp.errorMessage).toBe('Lien expiré');
    expect(comp.success).toBeFalse();
    expect(comp.loading).toBeFalse();
  });
});
