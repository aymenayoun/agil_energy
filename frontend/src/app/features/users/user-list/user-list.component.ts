import { Component, OnInit, inject, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { NotificationService } from '../../../core/services/notification.service';
import { AuthService } from '../../../core/services/auth.service';
import { StationService } from '../../../core/services/station.service';
import { environment } from '../../../../environments/environment';
import { Station } from '../../../core/models';

interface User {
  id: number;
  name: string;
  email: string;
  roleName: string;
  status: string;
  stationId?: number | null;
  stationName?: string | null;
  region?: string | null;
  createdAt: string;
}

interface CreateUserRequest {
  name: string;
  email: string;
  password: string;
  roleName: string;
  stationId?: number | null;
  region?: string | null;
}

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-list.component.html',
  styleUrls: ['./user-list.component.scss']
})
export class UserListComponent implements OnInit {
  private http     = inject(HttpClient);
  private notif    = inject(NotificationService);
  private auth     = inject(AuthService);
  private stationSvc = inject(StationService);

  private apiUrl = `${environment.apiUrl}/users`;

  users: User[] = [];
  availableStations: Station[] = [];
  loading = true;
  showForm = false;
  formError = '';
  currentUserId = this.auth.getCurrentUser()?.userId ?? 0;
  confirmingId: number | null = null;

  newUser: CreateUserRequest = this.emptyForm();

    // -------- Edit User --------
  showEditForm = false;
  editUser: { id: number } & import('../../../core/models').UpdateUserRequest = this.emptyEditForm();
  editError = '';
  editStationSearchQuery = '';
  editStationSearchOpen = false;

  get editFilteredStations(): Station[] {
    if (!this.editStationSearchQuery.trim()) return this.availableStations;
    const q = this.editStationSearchQuery.toLowerCase();
    return this.availableStations.filter(
      s => s.name.toLowerCase().includes(q) || s.region.toLowerCase().includes(q)
    );
  }

  get editSelectedStationLabel(): string {
    if (!this.editUser.stationId) return '';
    const s = this.availableStations.find(st => st.id === this.editUser.stationId);
    return s ? `${s.name} — ${s.region}` : '';
  }

  get isEditStationManagerRole(): boolean {
    return this.editUser.roleName === 'STATION_MANAGER';
  }

  get isEditManagerRole(): boolean {
    return this.editUser.roleName === 'MANAGER';
  }

  // ---------- Station search ----------
  stationSearchQuery = '';
  stationSearchOpen = false;

  get filteredStations(): Station[] {
    if (!this.stationSearchQuery.trim()) return this.availableStations;
    const q = this.stationSearchQuery.toLowerCase();
    return this.availableStations.filter(
      s => s.name.toLowerCase().includes(q) || s.region.toLowerCase().includes(q)
    );
  }

  get selectedStationLabel(): string {
    if (!this.newUser.stationId) return '';
    const s = this.availableStations.find(st => st.id === this.newUser.stationId);
    return s ? `${s.name} — ${s.region}` : '';
  }

  // ---------- Region list (derived from stations) ----------
  get availableRegions(): string[] {
    const regions = new Set(this.availableStations.map(s => s.region));
    return Array.from(regions).sort();
  }

  // ---------- Role checks ----------
  get activeCount(): number {
    return this.users.filter(u => u.status === 'ACTIVE').length;
  }

  get isStationManagerRole(): boolean {
    return this.newUser.roleName === 'STATION_MANAGER';
  }

  get isManagerRole(): boolean {
    return this.newUser.roleName === 'MANAGER';
  }

  ngOnInit(): void {
    this.loadUsers();
    this.loadStations();
  }

  loadUsers(): void {
    this.loading = true;
    this.http.get<any>(this.apiUrl).subscribe({
      next: (res) => { this.users = res.success ? res.data : []; this.loading = false; },
      error: () => { this.notif.error('Erreur lors du chargement des utilisateurs'); this.loading = false; }
    });
  }

  loadStations(): void {
    this.stationSvc.getAllStations().subscribe({
      next: (res) => { this.availableStations = res.success ? (res.data ?? []) : []; },
      error: () => { /* silent */ }
    });
  }

  onRoleChange(): void {
    if (!this.isStationManagerRole) {
      this.newUser.stationId = null;
      this.stationSearchQuery = '';
      this.stationSearchOpen = false;
    }
    if (!this.isManagerRole) {
      this.newUser.region = null;
    }
  }

  // ---------- Station search handlers ----------

  onStationSearchFocus(): void {
    this.stationSearchOpen = true;
  }

  selectStation(station: Station): void {
    this.newUser.stationId = station.id;
    this.stationSearchQuery = `${station.name} — ${station.region}`;
    this.stationSearchOpen = false;
  }

  clearStation(): void {
    this.newUser.stationId = null;
    this.stationSearchQuery = '';
    this.stationSearchOpen = false;
  }

  @HostListener('document:click', ['$event'])
  onDocClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.station-search-wrapper')) {
      this.stationSearchOpen = false;
    }
  }

  // ---------- CRUD ----------

  createUser(): void {
    this.formError = '';
    if (!this.newUser.name || !this.newUser.email || !this.newUser.password) {
      this.formError = 'Tous les champs sont obligatoires';
      return;
    }
    if (this.newUser.password.length < 8) {
      this.formError = 'Le mot de passe doit contenir au moins 8 caractères';
      return;
    }
    if (this.isStationManagerRole && !this.newUser.stationId) {
      this.formError = 'Une station doit être assignée au responsable de station';
      return;
    }
    if (this.isManagerRole && !this.newUser.region) {
      this.formError = 'Une région doit être assignée au gestionnaire régional';
      return;
    }

    this.http.post<any>(this.apiUrl, this.newUser).subscribe({
      next: (res) => {
        if (res.success) {
          this.notif.success(`Utilisateur ${this.newUser.name} créé avec succès`);
          this.showForm = false;
          this.newUser = this.emptyForm();
          this.stationSearchQuery = '';
          this.loadUsers();
        }
      },
      error: (err) => {
        this.formError = err.error?.message || 'Erreur lors de la création';
        this.notif.error(this.formError);
      }
    });
  }

  deactivate(id: number): void {
    this.confirmingId = null;
    this.http.put<any>(`${this.apiUrl}/${id}/deactivate`, {}).subscribe({
      next: () => { this.notif.success('Utilisateur désactivé'); this.loadUsers(); },
      error: () => this.notif.error('Erreur lors de la désactivation')
    });
  }

  activate(id: number): void {
    this.http.put<any>(`${this.apiUrl}/${id}/activate`, {}).subscribe({
      next: () => { this.notif.success('Utilisateur activé'); this.loadUsers(); },
      error: () => this.notif.error('Erreur lors de l\'activation')
    });
  }

  getRoleBadge(role: string): string {
    switch (role) {
      case 'ADMIN':           return 'badge badge-danger';
      case 'MANAGER':         return 'badge badge-info';
      case 'STATION_MANAGER': return 'badge badge-neutral';
      default:                return 'badge';
    }
  }

  getRoleLabel(role: string): string {
    switch (role) {
      case 'ADMIN':           return 'Administrateur';
      case 'MANAGER':         return 'Gestionnaire Régional';
      case 'STATION_MANAGER': return 'Resp. Station';
      default:                return role;
    }
  }

  getScopeLabel(user: User): string {
    if (user.stationName) return user.stationName;
    if (user.region) return user.region;
    return '-';
  }

  private emptyForm(): CreateUserRequest {
    return { name: '', email: '', password: '', roleName: 'STATION_MANAGER', stationId: null, region: null };
  }

  // -------- Edit User Methods --------

  openEdit(user: User): void {
    this.editUser = {
      id: user.id,
      name: user.name,
      email: user.email,
      roleName: user.roleName,
      stationId: user.stationId ?? null,
      region: user.region ?? null
    };
    this.editStationSearchQuery = user.stationName ? `${user.stationName}` : '';
    this.editStationSearchOpen = false;
    this.editError = '';
    this.showEditForm = true;
  }

  onEditRoleChange(): void {
    if (!this.isEditStationManagerRole) {
      this.editUser.stationId = null;
      this.editStationSearchQuery = '';
      this.editStationSearchOpen = false;
    }
    if (!this.isEditManagerRole) {
      this.editUser.region = null;
    }
  }

  onEditStationSearchFocus(): void {
    this.editStationSearchOpen = true;
  }

  selectEditStation(station: Station): void {
    this.editUser.stationId = station.id;
    this.editStationSearchQuery = `${station.name} — ${station.region}`;
    this.editStationSearchOpen = false;
  }

  clearEditStation(): void {
    this.editUser.stationId = null;
    this.editStationSearchQuery = '';
    this.editStationSearchOpen = false;
  }

  submitEdit(): void {
    this.editError = '';
    if (!this.editUser.name || !this.editUser.email) {
      this.editError = 'Le nom et l\'email sont obligatoires';
      return;
    }
    if (this.isEditStationManagerRole && !this.editUser.stationId) {
      this.editError = 'Une station doit être assignée au responsable de station';
      return;
    }
    if (this.isEditManagerRole && !this.editUser.region) {
      this.editError = 'Une région doit être assignée au gestionnaire régional';
      return;
    }

    const payload: any = {
      name: this.editUser.name,
      email: this.editUser.email,
      roleName: this.editUser.roleName,
      stationId: this.editUser.stationId,
      region: this.editUser.region
    };

    this.http.put<any>(`${this.apiUrl}/${this.editUser.id}`, payload).subscribe({
      next: (res) => {
        if (res.success) {
          this.notif.success('Utilisateur mis à jour avec succès');
          this.showEditForm = false;
          this.loadUsers();
        }
      },
      error: (err) => {
        this.editError = err.error?.message || 'Erreur lors de la mise à jour';
        this.notif.error(this.editError);
      }
    });
  }

  closeEdit(): void {
    this.showEditForm = false;
    this.editError = '';
  }

  private emptyEditForm() {
    return { id: 0, name: '', email: '', roleName: 'STATION_MANAGER', stationId: null as number | null, region: null as string | null };
  }
}
