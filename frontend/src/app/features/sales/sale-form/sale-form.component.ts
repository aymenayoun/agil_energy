import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SaleService } from '../../../core/services/sale.service';
import { StationService } from '../../../core/services/station.service';
import { Station, CreateSaleRequest } from '../../../core/models';
import { NotificationService } from '../../../core/services/notification.service';

@Component({
  selector: 'app-sale-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sale-form.component.html',
  styleUrls: ['./sale-form.component.scss']
})
export class SaleFormComponent implements OnInit {

  stations: Station[] = [];
  fuelTypes: { id: number; name: string }[] = [];
  sale: CreateSaleRequest = { stationId: 0, fuelTypeId: 0, saleDate: '', quantity: 0 };
  today = new Date().toISOString().split('T')[0];
  errorMessage = '';
  successMessage = '';
  submitting = false;

  constructor(
    private saleService: SaleService,
    private stationService: StationService,
    private router: Router,
    private notif: NotificationService
  ) {}

  ngOnInit(): void {
    this.sale.saleDate = this.today;
    this.stationService.getAllStations().subscribe(res => {
      if (res.success) this.stations = res.data;
    });
  }

  onStationChange(): void {
    const station = this.stations.find(s => s.id === this.sale.stationId);
    if (station && station.tanks) {
      this.fuelTypes = station.tanks.map(t => ({ id: t.fuelTypeId, name: t.fuelTypeName }));
    } else {
      this.fuelTypes = [];
    }
    this.sale.fuelTypeId = 0;
  }

  submit(): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.sale.stationId || !this.sale.fuelTypeId || !this.sale.saleDate || this.sale.quantity <= 0) {
      this.errorMessage = 'Veuillez remplir tous les champs correctement';
      return;
    }

    this.submitting = true;
    this.saleService.createSale(this.sale).subscribe({
      next: (res) => {
        this.submitting = false;
        if (res.success) {
          this.notif.success('Vente enregistrée ! Stock mis à jour automatiquement.');
          setTimeout(() => this.router.navigate(['/sales']), 1500);
        }
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Erreur lors de l\'enregistrement';
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/sales']);
  }
}
