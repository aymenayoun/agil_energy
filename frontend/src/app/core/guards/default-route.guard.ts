import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Guard for the empty child route ('').
 * Redirects to the user's role-appropriate default page:
 *   ADMIN / MANAGER       → /dashboard
 *   STATION_MANAGER       → /sales
 */
export const defaultRouteGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  router.navigate([authService.getDefaultRoute()]);
  return false; // always redirect, never render
};
