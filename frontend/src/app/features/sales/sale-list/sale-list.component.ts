import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { SaleService } from '../../../core/services/sale.service';
import { StationService } from '../../../core/services/station.service';
import { Sale, Station } from '../../../core/models';
import { NotificationService } from '../../../core/services/notification.service';
import { StationPickerComponent } from '../../../shared/components/station-picker/station-picker.component';

@Component({
  selector: 'app-sale-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, StationPickerComponent],
  templateUrl: './sale-list.component.html',
  styleUrls: ['./sale-list.component.scss']
})
export class SaleListComponent implements OnInit {

  private saleService    = inject(SaleService);
  private stationService = inject(StationService);
  private notif          = inject(NotificationService);

  sales: Sale[] = [];
  stations: Station[] = [];
  selectedStationId: number | null = null;
  startDate = '';
  endDate = '';
  loading = false;

  ngOnInit(): void {
    this.stationService.getAllStations().subscribe(res => {
      if (res.success) this.stations = res.data;
    });
  }

  loadSales(): void {
    if (!this.selectedStationId) return;
    this.loading = true;
    this.saleService.getSales(
      this.selectedStationId,
      undefined,
      this.startDate || undefined,
      this.endDate || undefined
    ).subscribe({
      next: (res) => { this.sales = res.success ? res.data : []; this.loading = false; },
      error: () => { this.loading = false; this.notif.error('Erreur lors du chargement des ventes'); }
    });
  }

  validate(id: number): void {
    this.saleService.validateSale(id).subscribe({
      next: () => { this.notif.success('Vente validée'); this.loadSales(); },
      error: () => this.notif.error('Erreur lors de la validation')
    });
  }
}
