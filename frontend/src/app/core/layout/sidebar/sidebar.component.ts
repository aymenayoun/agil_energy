import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AgilLogoComponent } from '../../../shared/components/agil-logo/agil-logo.component';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule, AgilLogoComponent],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent {

  userName = '';
  userRole = '';
  roleLabel = '';
  scopeLabel = '';

  constructor(private authService: AuthService) {
    const user = this.authService.getCurrentUser();
    this.userName = user?.name || '';
    this.userRole = user?.role || '';
    this.roleLabel = this.getRoleLabel(this.userRole);
    this.scopeLabel = this.getScopeLabel();
  }

  hasAnyRole(roles: string[]): boolean {
    return this.authService.hasAnyRole(roles);
  }

  private getRoleLabel(role: string): string {
    switch (role) {
      case 'ADMIN':           return 'Administrateur';
      case 'MANAGER':         return 'Gestionnaire Régional';
      case 'STATION_MANAGER': return 'Resp. Station';
      default:                return role;
    }
  }

  private getScopeLabel(): string {
    if (this.authService.isStationManager()) {
      return this.authService.getStationName() || '';
    }
    if (this.authService.isManager()) {
      return this.authService.getRegion() || '';
    }
    return '';
  }
}
