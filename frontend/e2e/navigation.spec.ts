import { test, expect } from '@playwright/test';
import { ADMIN } from './fixtures/credentials';

test.describe('App navigation (smoke)', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(ADMIN.email);
    await page.getByLabel(/mot de passe|password/i).fill(ADMIN.password);
    await page.getByRole('button', { name: /connexion|login|se connecter/i }).click();
    await page.waitForURL(/\/(dashboard|otp|verify-otp|home)/, { timeout: 15_000 });
  });

  test('can navigate to sales page after login', async ({ page }) => {
    test.skip(page.url().includes('otp'), 'OTP step required — skip in CI');

    // Try direct navigation (most reliable across menu/sidebar variations)
    await page.goto('/sales');

    // The page should load without redirecting back to login
    await expect(page).not.toHaveURL(/\/login/);

    // Some sales-related content should be visible
    await expect(
      page.getByText(/vente|sale|ventes|sales/i).first()
    ).toBeVisible({ timeout: 10_000 });
  });

  test('can navigate to stations page after login', async ({ page }) => {
    test.skip(page.url().includes('otp'), 'OTP step required — skip in CI');

    await page.goto('/stations');

    await expect(page).not.toHaveURL(/\/login/);
    await expect(
      page.getByText(/station/i).first()
    ).toBeVisible({ timeout: 10_000 });
  });
});
