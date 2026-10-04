import { Component, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { WebSocketService } from '../../services/websocket.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss']
})
export class NavbarComponent {
  @Output() menuToggle = new EventEmitter<void>();

  readonly ws = inject(WebSocketService);

  userName = '';
  currentTitle = 'AGIL Energy';
  userRole = '';
  userInitials = '';

  private titleMap: Record<string, string> = {
    'dashboard':            'Tableau de Bord',
    'stations':             'Gestion des Stations',
    'sales/new':            'Saisie des Ventes',
    'sales':                'Historique des Ventes',
    'stocks':               'Gestion des Stocks',
    'alerts/anomalies':     'Anomalies',
    'alerts':               'Alertes',
    'deliveries':           'Livraisons',
    'predictions/regions':  'Prévisions Régionales',
    'predictions/map':      'Carte Interactive',
    'predictions':          'Prévisions IA',
    'ai-assistant':         'Assistant IA',
    'users':                'Utilisateurs',
    'audit-log':            'Journal d\'Audit',
  };

  constructor(private authService: AuthService, private router: Router) {
    const user = this.authService.getCurrentUser();
    this.userName = user?.name || '';
    this.userRole = user?.role || '';
    this.userInitials = (user?.name || '')
      .split(' ')
      .map(n => n.charAt(0))
      .join('')
      .toUpperCase()
      .slice(0, 2);

    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd)
    ).subscribe((e: any) => {
      const path = e.urlAfterRedirects.replace(/^\//, '');
      const match = Object.keys(this.titleMap)
        .sort((a, b) => b.length - a.length)
        .find(k => path === k || path.startsWith(k + '/'));
      this.currentTitle = match ? this.titleMap[match] : 'AGIL Energy';
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
