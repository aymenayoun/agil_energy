import { Injectable, signal, inject } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { environment } from '../../../environments/environment';
import { NotificationService } from './notification.service';

export interface AlertEvent {
  id: number;
  stationId: number;
  stationName: string;
  alertType: 'STOCK_RUPTURE' | 'ANOMALY' | 'STOCK_INCOHERENT' | 'MISSING_ENTRY' | 'SALE_ANOMALY' | string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | string;
  message: string;
  status: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client?: Client;
  private demoTimers: ReturnType<typeof setTimeout>[] = [];
  private readonly notif = inject(NotificationService);

  readonly connected   = signal(false);
  readonly latestAlert = signal<AlertEvent | null>(null);
  readonly alertFeed   = signal<AlertEvent[]>([]);
  readonly unreadCount = signal(0);

  connect(): void {
    if (this.client?.active) return;

    // Demo mode has no STOMP broker. Simulate a couple of alerts so the
    // notification bell and the live feed aren't visibly dead.
    if (environment.demo) {
      this.startDemoFeed();
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) {
      console.warn('[WS] no token — skipping connect');
      return;
    }

    // environment.apiUrl = 'http://localhost:8082/api' → strip "/api" → 'http://localhost:8082'
    const base = environment.apiUrl.replace(/\/api\/?$/, '');
    const wsUrl = `${base}/ws`;

    this.client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      onConnect: () => {
        this.connected.set(true);
        this.client!.subscribe('/topic/alerts', (msg: IMessage) => this.onAlert(msg));
      },
      onStompError: frame => {
        console.error('[WS] STOMP error:', frame.headers['message'], frame.body);
      },
      onWebSocketClose: () => this.connected.set(false),
    });

    this.client.activate();
  }

  disconnect(): void {
    this.demoTimers.forEach(t => clearTimeout(t));
    this.demoTimers = [];
    this.client?.deactivate();
    this.client = undefined;
    this.connected.set(false);
  }

  resetUnread(): void { this.unreadCount.set(0); }

  // ---------- Demo mode ----------

  private startDemoFeed(): void {
    this.connected.set(true);

    const scripted: Array<{ delay: number; event: AlertEvent }> = [
      {
        delay: 6000,
        event: {
          id: 1, stationId: 17, stationName: 'Station Gafsa',
          alertType: 'STOCK_RUPTURE', severity: 'HIGH',
          message: 'Rupture estimée dans 2.7 jours (scénario P90). Stock : 1 275 L',
          status: 'ACTIVE', createdAt: new Date().toISOString()
        }
      },
      {
        delay: 20000,
        event: {
          id: 2, stationId: 2, stationName: 'Station Tunis Centre',
          alertType: 'SALE_ANOMALY', severity: 'HIGH',
          message: 'Vente anormalement élevée détectée — GASOIL (4 600 L), Z-score 15.72σ',
          status: 'ACTIVE', createdAt: new Date().toISOString()
        }
      }
    ];

    this.demoTimers = scripted.map(s =>
      setTimeout(() => this.emit(s.event), s.delay)
    );
  }

  // ---------- Internals ----------

  private onAlert(msg: IMessage): void {
    let ev: AlertEvent;
    try { ev = JSON.parse(msg.body); } catch { return; }
    this.emit(ev);
  }

  private emit(ev: AlertEvent): void {
    this.latestAlert.set(ev);
    this.alertFeed.update(list => [ev, ...list].slice(0, 100));
    this.unreadCount.update(n => n + 1);

    const toastType: 'error' | 'info' = ev.severity === 'HIGH' ? 'error' : 'info';
    const prefix = this.iconFor(ev.alertType);
    this.notif.show(`${prefix} ${ev.stationName} — ${ev.message}`, toastType, 7000);
  }

  private iconFor(type: string): string {
    switch (type) {
      case 'STOCK_RUPTURE':    return '⛽';
      case 'STOCK_INCOHERENT': return '⚠️';
      case 'MISSING_ENTRY':    return '📋';
      case 'SALE_ANOMALY':     return '📈';
      case 'ANOMALY':          return '🔎';
      default:                 return '🔔';
    }
  }
}
