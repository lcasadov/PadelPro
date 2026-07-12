import { defineConfig, devices } from '@playwright/test';

// E2E smoke de PadelPro (change e2e-smoke-tests, #205).
// Apunta al stack real levantado con docker-compose (frontend build + backend + db).
// El stack lo levanta CI (o el dev en local); Playwright NO gestiona el servidor.
//
// baseURL configurable con E2E_BASE_URL (default http://localhost:5173).
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173';
const isCI = !!process.env.CI;

export default defineConfig({
  testDir: './tests',
  // El arranque del backend puede tardar; damos margen a la primera navegación.
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  forbidOnly: isCI,
  retries: isCI ? 1 : 0,
  workers: 1,
  reporter: isCI
    ? [['list'], ['html', { open: 'never' }]]
    : [['list']],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    // Evita fallos por certificados en despliegues con TLS self-signed/staging.
    ignoreHTTPSErrors: true,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
