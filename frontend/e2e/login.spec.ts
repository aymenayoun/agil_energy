import { test, expect } from '@playwright/test';
import { ADMIN } from './fixtures/credentials';

test.describe('Login flow', () => {

  test('rejects invalid credentials with an error message', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel(/email/i).fill('wrong@example.com');
    await page.getByLabel(/mot de passe|password/i).fill('wrongpassword');
    await page.getByRole('button', { name: /connexion|login|se connecter/i }).click();

    // Expect to stay on login page and see an error
    await expect(page).toHaveURL(/\/login/);
    // Snackbar / alert / toast — match anything resembling an error message
    await expect(
      page.getByText(/incorrect|invalide|erreur|échec|failed/i).first()
    ).toBeVisible({ timeout: 10_000 });
  });

  test('accepts valid admin credentials and redirects to dashboard', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel(/email/i).fill(ADMIN.email);
    await page.getByLabel(/mot de passe|password/i).fill(ADMIN.password);
    await page.getByRole('button', { name: /connexion|login|se connecter/i }).click();

    // Successful login should leave the /login route within 10s.
    // OTP step is acceptable too — the requiresOtp branch redirects to an OTP page.
    await page.waitForURL(/\/(dashboard|otp|verify-otp|home)/, { timeout: 15_000 });
    await expect(page).not.toHaveURL(/\/login/);
  });
});
