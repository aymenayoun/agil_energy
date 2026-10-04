import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LoginRequest, AuthResponse } from '../../../core/models';
import { AgilLogoComponent } from '../../../shared/components/agil-logo/agil-logo.component';

type Step = 'credentials' | 'otp';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, AgilLogoComponent],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {

  step: Step = 'credentials';
  credentials: LoginRequest = { email: '', password: '' };
  otpCode = '';
  otpToken: string | null = null;
  otpExpiresInSeconds = 0;

  errorMessage = '';
  loading = false;
  showPassword = false;

  constructor(private authService: AuthService, private router: Router) {
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/dashboard']);
    }
  }

  onLogin(): void {
    this.errorMessage = '';
    this.loading = true;

    this.authService.login(this.credentials).subscribe({
      next: (response) => {
        this.loading = false;
        if (!response.success || !response.data) {
          this.errorMessage = response.message || 'Erreur de connexion';
          return;
        }
        const data = response.data;
        if (data.requiresOtp) {
          this.otpToken = data.otpToken ?? null;
          this.otpExpiresInSeconds = data.otpExpiresInSeconds ?? 0;
          this.step = 'otp';
          this.otpCode = '';
        } else {
          this.redirectAfterAuth(data);
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Email ou mot de passe incorrect';
      }
    });
  }

  onVerifyOtp(): void {
    if (!this.otpToken) return;
    this.errorMessage = '';
    this.loading = true;

    this.authService.verifyOtp({ otpToken: this.otpToken, code: this.otpCode }).subscribe({
      next: (response) => {
        this.loading = false;
        if (response.success && response.data?.token) {
          this.redirectAfterAuth(response.data);
        } else {
          this.errorMessage = response.message || 'Code invalide';
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Code invalide ou expiré';
      }
    });
  }

  backToCredentials(): void {
    this.step = 'credentials';
    this.otpCode = '';
    this.otpToken = null;
    this.errorMessage = '';
  }

  private redirectAfterAuth(data: AuthResponse): void {
    if (data.role === 'STATION_MANAGER') {
      this.router.navigate(['/sales']);
    } else {
      this.router.navigate(['/dashboard']);
    }
  }
}
