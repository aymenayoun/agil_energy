import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FuelTypeService } from '../../../core/services/fuel-type.service';
import { NotificationService } from '../../../core/services/notification.service';
import { FuelType, CreateFuelTypeRequest, UpdateFuelTypeRequest } from '../../../core/models';

@Component({
  selector: 'app-fuel-type-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './fuel-type-list.component.html',
  styleUrls: ['./fuel-type-list.component.scss']
})
export class FuelTypeListComponent implements OnInit {
  private fuelTypeSvc = inject(FuelTypeService);
  private notif       = inject(NotificationService);

  fuelTypes: FuelType[] = [];
  loading = true;

  // -------- Create --------
  showForm = false;
  formError = '';
  newFuelType: CreateFuelTypeRequest = this.emptyForm();

  // -------- Delete confirmation --------
  confirmingId: number | null = null;

  // -------- Edit --------
  showEditForm = false;
  editError = '';
  editFuelType: { id: number } & UpdateFuelTypeRequest = this.emptyEditForm();

  ngOnInit(): void {
    this.loadFuelTypes();
  }

  loadFuelTypes(): void {
    this.loading = true;
    this.fuelTypeSvc.getAllFuelTypes().subscribe({
      next: (res) => { this.fuelTypes = res.success ? (res.data ?? []) : []; this.loading = false; },
      error: () => { this.notif.error('Erreur lors du chargement des types de carburant'); this.loading = false; }
    });
  }

  // ---------- Create ----------

  createFuelType(): void {
    this.formError = '';
    if (!this.newFuelType.name?.trim()) {
      this.formError = 'Le nom du type de carburant est obligatoire';
      return;
    }

    this.fuelTypeSvc.createFuelType(this.newFuelType).subscribe({
      next: (res) => {
        if (res.success) {
          this.notif.success(`Type de carburant « ${this.newFuelType.name} » créé avec succès`);
          this.showForm = false;
          this.newFuelType = this.emptyForm();
          this.loadFuelTypes();
        }
      },
      error: (err) => {
        this.formError = err.error?.message || 'Erreur lors de la création';
        this.notif.error(this.formError);
      }
    });
  }

  // ---------- Edit ----------

  openEdit(fuelType: FuelType): void {
    this.editFuelType = {
      id: fuelType.id,
      name: fuelType.name,
      description: fuelType.description ?? ''
    };
    this.editError = '';
    this.showEditForm = true;
  }

  submitEdit(): void {
    this.editError = '';
    if (!this.editFuelType.name?.trim()) {
      this.editError = 'Le nom du type de carburant est obligatoire';
      return;
    }

    const payload: UpdateFuelTypeRequest = {
      name: this.editFuelType.name,
      description: this.editFuelType.description
    };

    this.fuelTypeSvc.updateFuelType(this.editFuelType.id, payload).subscribe({
      next: (res) => {
        if (res.success) {
          this.notif.success('Type de carburant mis à jour avec succès');
          this.showEditForm = false;
          this.loadFuelTypes();
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

  // ---------- Delete ----------

  deleteFuelType(id: number): void {
    this.confirmingId = null;
    this.fuelTypeSvc.deleteFuelType(id).subscribe({
      next: () => { this.notif.success('Type de carburant supprimé'); this.loadFuelTypes(); },
      error: (err) => this.notif.error(err.error?.message || 'Erreur lors de la suppression')
    });
  }

  // ---------- Helpers ----------

  private emptyForm(): CreateFuelTypeRequest {
    return { name: '', description: '' };
  }

  private emptyEditForm() {
    return { id: 0, name: '', description: '' };
  }
}
