import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AgilLogoComponent } from '../../../shared/components/agil-logo/agil-logo.component';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, AgilLogoComponent],
  templateUrl: './reset-password.component.html',
  styleUrls: ['../forgot-password/forgot-password.component.scss']
})
export class ResetPasswordComponent implements OnInit {

  token = '';
  password = '';
  confirmPassword = '';
  showPassword = false;

  loading = false;
  success = false;
  errorMessage = '';
  tokenMissing = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.tokenMissing = true;
    }
  }

  // ── Live password-policy checks (mirror the backend rules) ──
  get hasMinLength(): boolean { return this.password.length >= 8; }
  get hasUpper(): boolean { return /[A-Z]/.test(this.password); }
  get hasLower(): boolean { return /[a-z]/.test(this.password); }
  get hasDigit(): boolean { return /\d/.test(this.password); }
  get passwordsMatch(): boolean {
    return this.confirmPassword.length > 0 && this.password === this.confirmPassword;
  }
  get isValid(): boolean {
    return this.hasMinLength && this.hasUpper && this.hasLower && this.hasDigit && this.passwordsMatch;
  }

  onSubmit(): void {
    if (!this.isValid || !this.token) return;
    this.errorMessage = '';
    this.loading = true;

    this.authService.resetPassword(this.token, this.password).subscribe({
      next: () => {
        this.loading = false;
        this.success = true;
        // Send them to login after a short beat.
        setTimeout(() => this.router.navigate(['/login']), 2500);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Lien invalide ou expiré. Refaites une demande.';
      }
    });
  }
}
