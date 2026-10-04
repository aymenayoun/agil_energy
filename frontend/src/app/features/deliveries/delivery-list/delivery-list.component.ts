import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DeliveryService } from '../../../core/services/delivery.service';
import { StationService } from '../../../core/services/station.service';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Delivery, Station } from '../../../core/models';
import { StationPickerComponent } from '../../../shared/components/station-picker/station-picker.component';

@Component({
  selector: 'app-delivery-list',
  standalone: true,
  imports: [CommonModule, FormsModule, StationPickerComponent],
  templateUrl: './delivery-list.component.html',
  styleUrls: ['./delivery-list.component.scss']
})
export class DeliveryListComponent implements OnInit {
  private deliveryService = inject(DeliveryService);
  private stationService  = inject(StationService);
  private authService     = inject(AuthService);
  private notif           = inject(NotificationService);

  deliveries: Delivery[] = [];
  stations: Station[] = [];
  selectedStationId: number | null = null;
  loading = true;
  totalVolume = 0;
  userRole: string = '';

  ngOnInit(): void {
    this.userRole = this.authService.getRole() || '';
    this.stationService.getAllStations().subscribe({
      next: (res) => { if (res.success) this.stations = res.data; },
      error: () => this.notif.error('Impossible de charger les stations')
    });
    this.loadDeliveries();
  }

  loadDeliveries(): void {
    this.loading = true;
    this.deliveryService.getDeliveries(this.selectedStationId ?? undefined).subscribe({
      next: (res) => {
        if (res.success) {
          this.deliveries = res.data;
          this.totalVolume = res.data.reduce((sum, d) => sum + d.quantity, 0);
        }
        this.loading = false;
      },
      error: () => {
        this.notif.error('Erreur lors du chargement des livraisons');
        this.loading = false;
      }
    });
  }

  canValidate(): boolean {
    return this.userRole === 'ADMIN' || this.userRole === 'MANAGER';
  }

  validateDelivery(delivery: Delivery): void {
    if (delivery.validated) return;
    this.deliveryService.validateDelivery(delivery.id).subscribe({
      next: (res) => {
        if (res.success) {
          delivery.validated = true;
          this.notif.success('Livraison validée avec succès');
        }
      },
      error: () => this.notif.error('Erreur lors de la validation')
    });
  }
}
