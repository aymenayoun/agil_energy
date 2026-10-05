/**
 * Demo mode has no auth server, so a session is seeded into localStorage
 * before the app bootstraps. Shapes match AuthResponse, which is what
 * AuthService.persistIfAuthenticated() normally writes.
 */

export type DemoRole = 'ADMIN' | 'MANAGER' | 'STATION_MANAGER';

export const DEMO_USERS: Record<DemoRole, Record<string, unknown>> = {
  ADMIN: {
    token: 'demo-token-admin',
    refreshToken: 'demo-refresh-admin',
    name: 'Aymen',
    email: 'admin@demo.tn',
    role: 'ADMIN',
    region: null,
    stationId: null,
    stationName: null,
    requiresOtp: false
  },
  MANAGER: {
    token: 'demo-token-manager',
    refreshToken: 'demo-refresh-manager',
    name: 'Responsable Régional',
    email: 'manager@demo.tn',
    role: 'MANAGER',
    region: 'Tunis',
    stationId: null,
    stationName: null,
    requiresOtp: false
  },
  STATION_MANAGER: {
    token: 'demo-token-station',
    refreshToken: 'demo-refresh-station',
    name: 'Responsable Station',
    email: 'station@demo.tn',
    role: 'STATION_MANAGER',
    region: null,
    stationId: 2,
    stationName: 'Station La Marsa',
    requiresOtp: false
  }
};

export const DEMO_ROLE_KEY = 'demo-role';

export function currentDemoRole(): DemoRole {
  const r = localStorage.getItem(DEMO_ROLE_KEY) as DemoRole | null;
  return r && r in DEMO_USERS ? r : 'ADMIN';
}

/** Seeds the session. Called before bootstrapApplication so guards see a user. */
export function seedDemoSession(role: DemoRole = currentDemoRole()): void {
  const u = DEMO_USERS[role];
  localStorage.setItem(DEMO_ROLE_KEY, role);
  localStorage.setItem('token', u['token'] as string);
  localStorage.setItem('refreshToken', u['refreshToken'] as string);
  localStorage.setItem('user', JSON.stringify(u));
}

/** Switches role and reloads so every component re-fetches under the new scope. */
export function switchDemoRole(role: DemoRole): void {
  seedDemoSession(role);
  // Navigate to the base URL (a real file) rather than a deep link, which would
  // 404 on GitHub Pages. defaultRouteGuard then sends each role to its landing
  // page: ADMIN/MANAGER to /dashboard, STATION_MANAGER to /sales.
  location.assign(document.baseURI);
}
