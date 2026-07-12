import { test, expect } from '@playwright/test';
import { login, crearReservaDesdeHome } from './helpers';

// Journey de pago en EFECTIVO gestionado por el ADMIN (change pagos-simulador-gestion):
// crear una reserva (pago PENDING) → panel de Cobros (/admin/pagos) → filtrar
// Pendientes → "Marcar como pagada (efectivo)" → la reserva pasa a PAID.
// Endpoint: POST /api/admin/pagos/{reservaId}/efectivo. Determinista y autocontenido.

test('pago efectivo: el admin marca una reserva pendiente como pagada', async ({ page }) => {
  await login(page);
  const reservaId = await crearReservaDesdeHome(page); // reserva PENDING recién creada

  // Confirmación → Mis Reservas → Inicio → Cobros (navegación cliente; token en memoria).
  await page.getByRole('button', { name: /Ver mis reservas/i }).click();
  await expect(page).toHaveURL(/\/reservas\/mias/);
  await page.getByRole('button', { name: /^Inicio$/ }).click();
  await expect(page).toHaveURL(/\/home/);
  await page.getByRole('link', { name: /Cobros/i }).click();
  await expect(page).toHaveURL(/\/admin\/pagos/);

  // Filtrar Pendientes y localizar la fila de NUESTRA reserva por su id.
  await page.getByRole('button', { name: /^Pendientes$/ }).click();
  const fila = page.locator('tr', { hasText: reservaId });
  await expect(fila).toBeVisible();
  await fila.getByRole('button', { name: /Marcar como pagada/i }).click();

  // Tras marcar en efectivo, la reserva sale de Pendientes y aparece en Pagadas como "Pagado".
  await page.getByRole('button', { name: /^Pagadas$/ }).click();
  const filaPagada = page.locator('tr', { hasText: reservaId });
  await expect(filaPagada).toBeVisible();
  await expect(filaPagada.getByText(/Pagado/i)).toBeVisible();
});
