import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StockService } from '../../../core/services/stock.service';
import { StationService } from '../../../core/services/station.service';
import { DeliveryService } from '../../../core/services/delivery.service';
import { Station, Tank, CreateDeliveryRequest } from '../../../core/models';
import { NotificationService } from '../../../core/services/notification.service';
import { StationPickerComponent } from '../../../shared/components/station-picker/station-picker.component';

@Component({
  selector: 'app-stock-list',
  standalone: true,
  imports: [CommonModule, FormsModule, StationPickerComponent],
  templateUrl: './stock-list.component.html',
  styleUrls: ['./stock-list.component.scss']
})
export class StockListComponent implements OnInit {

  stations: Station[] = [];
  tanks: Tank[] = [];
  selectedStationId: number | null = null;
  showDeliveryForm = false;
  selectedTank: Tank | null = null;
  delivery: CreateDeliveryRequest = { stationId: 0, fuelTypeId: 0, deliveryDate: '', quantity: 0 };
  deliveryError = '';

  constructor(
    private stockService: StockService,
    private stationService: StationService,
    private deliveryService: DeliveryService,
    private notif: NotificationService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.stationService.getAllStations().subscribe({
      next: (res) => {
        if (res.success) this.stations = res.data;
        const stationIdParam = this.route.snapshot.queryParamMap.get('stationId');
        if (stationIdParam) {
          this.selectedStationId = Number(stationIdParam);
          this.loadStocks();
        }
      },
      error: (err) => {
        this.notif.error(err.error?.message || 'Erreur lors du chargement des stations');
      }
    });
  }

  loadStocks(): void {
    if (!this.selectedStationId) { this.tanks = []; return; }
    this.stockService.getStocksByStation(this.selectedStationId).subscribe({
      next: (res) => {
        if (res.success) this.tanks = res.data;
      },
      error: (err) => {
        this.notif.error(err.error?.message || 'Erreur lors du chargement des stocks');
      }
    });
  }

  loadCritical(): void {
    this.selectedStationId = null;
    this.stockService.getCriticalTanks().subscribe({
      next: (res) => {
        if (res.success) this.tanks = res.data;
      },
      error: (err) => {
        this.notif.error(err.error?.message || 'Erreur lors du chargement des réservoirs critiques');
      }
    });
  }

  openDelivery(tank: Tank): void {
    this.selectedTank = tank;
    this.delivery = {
      stationId: tank.stationId,
      fuelTypeId: tank.fuelTypeId,
      deliveryDate: new Date().toISOString().split('T')[0],
      quantity: 0
    };
    this.deliveryError = '';
    this.showDeliveryForm = true;
  }

  submitDelivery(): void {
    if (this.delivery.quantity <= 0) {
      this.deliveryError = 'La quantité doit être positive';
      return;
    }
    this.deliveryService.createDelivery(this.delivery).subscribe({
      next: () => {
        this.showDeliveryForm = false;
        this.loadStocks();
      },
      error: (err) => {
        this.deliveryError = err.error?.message || 'Erreur lors de la livraison';
        this.notif.error(this.deliveryError);
      }
    });
  }
}
