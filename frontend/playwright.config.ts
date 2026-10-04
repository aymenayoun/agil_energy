/// <reference types="node" />
import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for AGIL Energy E2E tests.
 *
 * Tests assume the full Docker Compose stack is running:
 *   - frontend on http://localhost:4200
 *   - backend on http://localhost:8082
 *   - mysql on localhost:3307
 *
 * To run locally: `docker compose up -d` then `npx playwright test`
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,           // sequential — auth state is shared across tests
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,                     // single worker — avoids race conditions on shared backend
  reporter: [
    ['html', { open: 'never' }],
    ['list'],
  ],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
