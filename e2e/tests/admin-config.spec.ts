import { test, expect } from '@playwright/test';
import { login } from './helpers';

// Journey de configuración del club por el ADMIN (change admin-config-club):
// login admin → Home → "Configuración" (/admin/config) → cambiar el precio por hora
// → Guardar → recargar la página (re-login, el token vive en memoria) → el precio
// persiste. Determinista y autocontenido sobre el stack real.

test('config: el admin cambia el precio por hora y persiste', async ({ page }) => {
  await login(page);

  // Home → Configuración (navegación cliente; token en memoria, sin page.goto privado).
  await page.getByRole('link', { name: /^Configuración/i }).click();
  await expect(page).toHaveURL(/\/admin\/config/);

  // Fija un precio nuevo determinista y guarda.
  const nuevoPrecio = '17.5';
  const precio = page.getByLabel(/Precio por hora/i);
  await expect(precio).toBeVisible();
  await precio.fill(nuevoPrecio);
  await page.getByRole('button', { name: /Guardar configuración/i }).click();

  // Confirmación de guardado.
  await expect(page.getByRole('status')).toHaveText(/guardada/i);

  // Re-login (recarga completa perdería el token en memoria) y volver a Configuración:
  // el precio persistido debe reflejarse.
  await login(page);
  await page.getByRole('link', { name: /^Configuración/i }).click();
  await expect(page).toHaveURL(/\/admin\/config/);
  await expect(page.getByLabel(/Precio por hora/i)).toHaveValue(nuevoPrecio);
});
