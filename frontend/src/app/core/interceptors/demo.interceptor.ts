import { HttpInterceptorFn, HttpResponse, HttpErrorResponse, HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, of, throwError, map, catchError } from 'rxjs';
import { environment } from '../../../environments/environment';

const MOCK = 'assets/mock';

const ok = (body: unknown) => of(new HttpResponse({ status: 200, body }));

const readonlyStub = () => ok({
  success: false,
  message: 'Mode démonstration : les modifications sont désactivées.',
  timestamp: new Date().toISOString()
});

/** Role of the simulated user, read from the session the demo bootstrap seeded. */
function currentRole(): string {
  try {
    const u = JSON.parse(localStorage.getItem('user') || '{}');
    return u.role || 'ADMIN';
  } catch { return 'ADMIN'; }
}

/** Fixture folder per role. Explicit map — GitHub Pages paths are case-sensitive. */
const ROLE_DIR: Record<string, string> = {
  ADMIN: 'admin',
  MANAGER: 'manager',
  STATION_MANAGER: 'station'
};

/** Endpoints a STATION_MANAGER must not reach — returns the captured 403. */
const STATION_FORBIDDEN = [/^\/dashboard/, /^\/users/, /^\/audit-logs/, /^\/predictions/];

/** Maps an API path+query to a fixture filename, or null if unmapped. */
function fixtureFor(path: string, query: URLSearchParams): string | null {
  const stationId = query.get('stationId');
  const fuelTypeId = query.get('fuelTypeId');
  const region = query.get('region');

  if (path === '/dashboard')                 return 'ROLE/dashboard.json';
  if (path === '/stations')                  return region ? `stations-region-${region}.json` : 'ROLE/stations.json';
  if (/^\/stations\/\d+$/.test(path))        return `station-${path.split('/')[2]}.json`;
  if (path === '/alerts')                    return 'ROLE/alerts.json';
  if (/^\/alerts\/station\/\d+$/.test(path)) return `alerts-station-${path.split('/')[3]}.json`;
  if (path === '/alerts/stats/anomalies')    return 'ROLE/alerts-stats-anomalies.json';
  if (path === '/audit-logs')                return 'audit-logs.json';
  if (path === '/deliveries')                return stationId ? `deliveries-station-${stationId}.json` : 'deliveries.json';
  if (path === '/sales')                     return stationId ? `sales-station-${stationId}.json` : 'sales-station-1.json';
  if (path === '/stocks/critical')           return 'stocks-critical.json';
  if (/^\/stocks\/\d+$/.test(path))          return `stocks-${path.split('/')[2]}.json`;
  if (path === '/fuel-types')                return 'fuel-types.json';
  if (path === '/users')                     return 'users.json';
  if (path === '/predictions/ia/health')     return 'ia-health.json';
  if (path === '/predictions/regions')       return 'ROLE/predictions-regions.json';

  if (/^\/predictions\/regions\/[^/]+\/fuel-types$/.test(path)) {
    return 'region-fuel-types.json';
  }

  if (/^\/predictions\/\d+$/.test(path)) {
    const id = path.split('/')[2];
    return fuelTypeId
      ? `predictions-station-${id}-${fuelTypeId}.json`
      : `predictions-station-${id}.json`;
  }

  return null;
}

/** POST bodies that have canned answers. */
function postFixture(path: string, body: any): string | null {
  if (path === '/predictions/generate') return 'prediction.json';

  if (path === '/predictions/generate/region') {
    const r = body?.region ?? 'Tunis';
    const f = body?.fuelTypeId ?? 1;
    return `predictions-regions-${r}-${f}.json`;
  }

  if (path === '/ai/explain') return 'ai-explain.json';

  if (path === '/ai/chat') {
    const q = (body?.question || '').toLowerCase();
    if (q.includes('ramadan'))                        return 'chat-ramadan.json';
    if (q.includes('rupture') || q.includes('stock')) return 'chat-rupture.json';
    return 'chat-default.json';
  }

  return null;
}

export const demoInterceptor: HttpInterceptorFn = (req, next) => {
  if (!environment.demo) return next(req);

  const http = inject(HttpClient);

  // Let fixture files themselves through, or we'd recurse forever.
  if (req.url.startsWith(MOCK) || req.url.includes('/assets/')) return next(req);
  if (!req.url.includes('/api')) return next(req);

  const url = new URL(req.url, location.origin);
  const path = url.pathname.replace(/^.*\/api/, '');
  const role = currentRole();

  // Auth endpoints: never hit the network in demo mode.
  if (path.startsWith('/auth/')) {
    return ok({ success: true, data: null, timestamp: new Date().toISOString() });
  }

  // RBAC refusals, served from the real captured 403 body.
  if (role === 'STATION_MANAGER' && STATION_FORBIDDEN.some(r => r.test(path))) {
    return http.get(`${MOCK}/403.json`).pipe(
      map(body => { throw new HttpErrorResponse({ status: 403, error: body, url: req.url }); }),
      catchError(e => throwError(() => e))
    ) as Observable<never>;
  }

  const name = req.method === 'GET'
    ? fixtureFor(path, url.searchParams)
    : postFixture(path, req.body);

  if (!name) {
    if (req.method !== 'GET') return readonlyStub();
    console.warn(`[demo] no fixture mapped for ${req.method} ${path}`);
    return ok({ success: true, data: [], message: 'demo: unmapped', timestamp: new Date().toISOString() });
  }

  // ROLE placeholder: try the role-specific file, fall back to the shared one.
  const dir = ROLE_DIR[role] ?? 'admin';
  const tryUrls = name.includes('ROLE/')
    ? [`${MOCK}/${name.replace('ROLE/', dir + '/')}`, `${MOCK}/${name.replace('ROLE/', '')}`]
    : [`${MOCK}/${name}`];

  const attempt = (i: number): Observable<any> =>
    http.get(tryUrls[i]).pipe(
      map(body => new HttpResponse({ status: 200, body })),
      catchError(() => {
        if (i + 1 < tryUrls.length) return attempt(i + 1);
        console.warn(`[demo] fixture missing: ${tryUrls.join(' | ')}`);
        return ok({ success: true, data: [], message: 'demo: fixture missing', timestamp: new Date().toISOString() });
      })
    );

  return attempt(0);
};
