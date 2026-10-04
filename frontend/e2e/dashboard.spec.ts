import { test, expect } from '@playwright/test';
import { ADMIN } from './fixtures/credentials';

test.describe('Dashboard', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(ADMIN.email);
    await page.getByLabel(/mot de passe|password/i).fill(ADMIN.password);
    await page.getByRole('button', { name: /connexion|login|se connecter/i }).click();
    await page.waitForURL(/\/(dashboard|otp|verify-otp|home)/, { timeout: 15_000 });
  });

  test('dashboard page loads and shows main content', async ({ page }) => {
    // If we landed on OTP page, this test isn't applicable in CI (no email)
    test.skip(!page.url().includes('dashboard') && !page.url().includes('home'),
              'OTP step required — not testable without email integration');

    // Wait for any heading or KPI card to be visible — proves the dashboard rendered
    await expect(
      page.locator('h1, h2, .kpi, .card, mat-card').first()
    ).toBeVisible({ timeout: 10_000 });

    // The page should contain at least one numeric value (KPI) or a chart canvas
    const hasContent = await page.evaluate(() => {
      const text = document.body.innerText;
      const hasNumber = /\d/.test(text);
      const hasCanvas = document.querySelector('canvas, svg.chart, .chart');
      return hasNumber || !!hasCanvas;
    });
    expect(hasContent).toBeTruthy();
  });
});
