import { Injectable, signal } from '@angular/core';

export interface Notification {
  id: number;
  type: 'success' | 'error' | 'info';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  notifications = signal<Notification[]>([]);
  private counter = 0;

  show(message: string, type: 'success' | 'error' | 'info' = 'info', duration = 4000): void {
    const id = ++this.counter;
    this.notifications.update(n => [...n, { id, type, message }]);
    setTimeout(() => this.dismiss(id), duration);
  }

  success(message: string): void { this.show(message, 'success'); }
  error(message: string): void   { this.show(message, 'error', 6000); }
  info(message: string): void    { this.show(message, 'info'); }

  dismiss(id: number): void {
    this.notifications.update(n => n.filter(x => x.id !== id));
  }
}
