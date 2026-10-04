import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { NotificationService } from '../../core/services/notification.service';
import { AuthService } from '../../core/services/auth.service';
import { environment } from '../../../environments/environment';

interface AuditLogEntry {
  id: number;
  date: string;
  utilisateur: string;
  role: string;
  action: string;
  action_label: string;
  entite: string;
  entite_id: number | null;
  details: string | null;
  ip: string | null;
}

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './audit-log.component.html',
  styleUrls: ['./audit-log.component.scss']
})
export class AuditLogComponent implements OnInit {
  private http = inject(HttpClient);
  private notif = inject(NotificationService);
  private auth = inject(AuthService);

  private apiUrl = `${environment.apiUrl}/audit-logs`;

  logs: AuditLogEntry[] = [];
  filteredLogs: AuditLogEntry[] = [];
  loading = true;
  total = 0;

  // Filters
  startDate = '';
  endDate = '';
  actionFilter = '';
  userFilter = '';
  limit = 100;

  // Available filter options (populated from data)
  availableActions: string[] = [];
  availableUsers: string[] = [];

  expandedId: number | null = null;

  ngOnInit(): void {
    this.loadLogs();
  }

  loadLogs(): void {
    this.loading = true;
    let url = `${this.apiUrl}?limit=${this.limit}`;

    if (this.startDate && this.endDate) {
      url += `&startDate=${this.startDate}&endDate=${this.endDate}`;
    }

    this.http.get<any>(url).subscribe({
      next: (res) => {
        if (res.success) {
          this.logs = res.data.logs || [];
          this.total = res.data.total || 0;
          this.updateFilterOptions();
          this.applyFilters();
        }
        this.loading = false;
      },
      error: () => {
        this.notif.error('Erreur lors du chargement des logs');
        this.loading = false;
      }
    });
  }

  updateFilterOptions(): void {
    this.availableActions = [...new Set(this.logs.map(l => l.action_label))].sort();
    this.availableUsers = [...new Set(this.logs.map(l => l.utilisateur))].sort();
  }

  applyFilters(): void {
    this.filteredLogs = this.logs.filter(log => {
      if (this.actionFilter && log.action_label !== this.actionFilter) return false;
      if (this.userFilter && log.utilisateur !== this.userFilter) return false;
      return true;
    });
  }

  resetFilters(): void {
    this.startDate = '';
    this.endDate = '';
    this.actionFilter = '';
    this.userFilter = '';
    this.loadLogs();
  }

  exportCsv(): void {
    let url = `${this.apiUrl}/export`;
    if (this.startDate && this.endDate) {
      url += `?startDate=${this.startDate}&endDate=${this.endDate}`;
    }

    const token = this.auth.getToken();

    // Use fetch with auth header, then trigger download
    fetch(url, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    .then(response => {
      if (!response.ok) throw new Error('Export failed');
      return response.blob();
    })
    .then(blob => {
      const filename = `audit_log_agil_${new Date().toISOString().slice(0, 10)}.csv`;
      const downloadUrl = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = downloadUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(downloadUrl);
      this.notif.success('Export CSV téléchargé');
    })
    .catch(() => this.notif.error('Erreur lors de l\'export'));
  }

  toggleDetails(id: number): void {
    this.expandedId = this.expandedId === id ? null : id;
  }

  getRoleBadge(role: string): string {
    switch (role) {
      case 'ADMIN': return 'badge badge-danger';
      case 'MANAGER': return 'badge badge-info';
      case 'STATION_MANAGER': return 'badge badge-neutral';
      case 'SYSTEM': return 'badge badge-warning';
      default: return 'badge';
    }
  }

  getActionIcon(action: string): string {
    if (action.startsWith('CREATE')) return 'add_circle';
    if (action.startsWith('VALIDATE')) return 'check_circle';
    if (action.startsWith('DEACTIVATE')) return 'block';
    if (action.startsWith('ACTIVATE')) return 'power_settings_new';
    if (action.startsWith('RESOLVE')) return 'done_all';
    if (action.startsWith('ADJUST')) return 'tune';
    if (action.includes('GENERATE')) return 'psychology';
    return 'event';
  }

  getActionColor(action: string): string {
    if (action.startsWith('CREATE')) return 'var(--green)';
    if (action.startsWith('VALIDATE')) return 'var(--blue)';
    if (action.startsWith('DEACTIVATE')) return 'var(--red)';
    if (action.startsWith('ACTIVATE')) return 'var(--green)';
    if (action.startsWith('RESOLVE')) return 'var(--green)';
    return 'var(--text-muted)';
  }

  formatDetails(details: string | null): string {
    if (!details) return '';
    try {
      const obj = JSON.parse(details);
      return Object.entries(obj)
        .map(([k, v]) => `${k}: ${v}`)
        .join(' • ');
    } catch {
      return details;
    }
  }
}
