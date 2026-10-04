import { Component } from '@angular/core';
import { environment } from '../../../environments/environment';
import { DemoRole, currentDemoRole, switchDemoRole } from './demo-session';

@Component({
  selector: 'app-demo-switcher',
  standalone: true,
  template: `
    @if (show) {
      <div class="demo-bar">
        <span class="tag">DÉMO</span>
        <span class="txt">Données simulées — aucun backend. Rôle :</span>
        @for (r of roles; track r.value) {
          <button
            type="button"
            [class.on]="r.value === active"
            (click)="pick(r.value)">{{ r.label }}</button>
        }
      </div>
    }
  `,
  styles: [`
    .demo-bar{
      position:fixed; left:50%; bottom:16px; transform:translateX(-50%);
      z-index:9999; display:flex; align-items:center; gap:8px; flex-wrap:wrap;
      padding:8px 14px; border-radius:999px;
      background:rgba(20,20,20,.94); border:1px solid rgba(255,255,255,.14);
      box-shadow:0 8px 28px rgba(0,0,0,.45);
      font:500 12px/1.2 system-ui,sans-serif; color:#d8d4cc;
      max-width:calc(100vw - 24px);
    }
    .tag{ background:#e0a33a; color:#1a1a1a; border-radius:999px; padding:2px 8px; font-weight:700; letter-spacing:.04em; }
    .txt{ opacity:.75; }
    button{
      font:inherit; cursor:pointer; color:#d8d4cc;
      background:transparent; border:1px solid rgba(255,255,255,.2);
      border-radius:999px; padding:4px 11px;
    }
    button:hover{ border-color:#e0a33a; color:#e0a33a; }
    button.on{ background:#e0a33a; border-color:#e0a33a; color:#1a1a1a; font-weight:600; }
    @media (max-width:560px){ .txt{ display:none; } }
  `]
})
export class DemoSwitcherComponent {
  readonly show = environment.demo;
  readonly active: DemoRole = currentDemoRole();
  readonly roles: { value: DemoRole; label: string }[] = [
    { value: 'ADMIN',           label: 'Admin' },
    { value: 'MANAGER',         label: 'Régional (Tunis)' },
    { value: 'STATION_MANAGER', label: 'Station' }
  ];

  pick(r: DemoRole): void {
    if (r !== this.active) switchDemoRole(r);
  }
}
