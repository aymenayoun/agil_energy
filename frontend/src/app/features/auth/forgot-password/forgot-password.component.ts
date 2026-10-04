import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AgilLogoComponent } from '../../../shared/components/agil-logo/agil-logo.component';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, AgilLogoComponent],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss']
})
export class ForgotPasswordComponent {

  email = '';
  loading = false;
  submitted = false;       // once true, we show the neutral confirmation panel
  errorMessage = '';

  constructor(private authService: AuthService) {}

  onSubmit(): void {
    this.errorMessage = '';
    this.loading = true;

    this.authService.forgotPassword(this.email).subscribe({
      next: () => {
        this.loading = false;
        // Always show the same confirmation, whether or not the email exists.
        this.submitted = true;
      },
      error: (err) => {
        this.loading = false;
        // The backend returns 200 for the happy path; a non-200 here is a real
        // problem (validation/server). Show a generic message, no enumeration.
        this.errorMessage = err.error?.message || 'Une erreur est survenue. Réessayez plus tard.';
      }
    });
  }
}
