import { test, expect } from '@playwright/test';
import { login } from './helpers';

// Journey: navegación básica del flujo de reservas (change e2e-journeys).
//
// Desde una página del flujo (Disponibilidad), el control "Inicio" del header
// devuelve a /home mediante navegación cliente (no recarga: el token vive en
// memoria — RN-AUTH-09).
test('el control "Inicio" vuelve a /home desde Disponibilidad', async ({ page }) => {
  await login(page);

  // Home → Disponibilidad.
  await page.getByRole('button', { name: /reservar pista/i }).click();
  await expect(page).toHaveURL(/\/reservas\/disponibilidad/);

  // Control "Inicio" del header → /home.
  await page.getByRole('button', { name: /^Inicio$/ }).click();
  await expect(page).toHaveURL(/\/home/);

  // Confirma que estamos realmente en Home (acción principal visible).
  await expect(page.getByRole('button', { name: /reservar pista/i })).toBeVisible();
});
