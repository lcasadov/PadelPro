import { test, expect } from '@playwright/test';
import { login, crearReservaDesdeHome } from './helpers';

// Journey de "pagar ahora" vía el SIMULADOR de pago (change pagos-simulador-gestion).
// Mientras Redsys real no está configurado, "pagar ahora" enruta al simulador
// (/pagos/simulador): tarjeta + caducidad + CVC → POST /api/pagos/simular. Se usan
// las TARJETAS MÁGICAS deterministas: 4111… aprueba siempre, 4000…0002 rechaza.
// El pago real por TPV externo de Redsys queda fuera de alcance (web externa).

const TARJETA_APRUEBA = '4111 1111 1111 1111';
const TARJETA_RECHAZA = '4000 0000 0000 0002';

async function irAlSimuladorDeUnaReservaPendiente(page: import('@playwright/test').Page) {
  await login(page);
  await crearReservaDesdeHome(page); // deja una reserva PENDING del admin (owner)
  // Desde la confirmación → Mis Reservas (navegación cliente, el token vive en memoria).
  await page.getByRole('button', { name: /Ver mis reservas/i }).click();
  await expect(page).toHaveURL(/\/reservas\/mias/);
  // La reserva pendiente muestra "Pagar ahora"; se paga la primera.
  await page.getByRole('button', { name: /^Pagar ahora$/ }).first().click();
  await expect(page).toHaveURL(/\/pagos\/simulador/);
}

test('pagar ahora (simulador): tarjeta que aprueba → pago realizado', async ({ page }) => {
  await irAlSimuladorDeUnaReservaPendiente(page);

  await page.getByLabel('Número de tarjeta').fill(TARJETA_APRUEBA);
  await page.getByLabel('Caducidad (MM/AA)').fill('12/30');
  await page.getByLabel('CVC').fill('123');
  await page.getByRole('button', { name: /^Pagar$/ }).click();

  // APPROVED → redirige a la pantalla de pago OK (PagoConfirmadoPage).
  await expect(page).toHaveURL(/\/pagos\/ok/);
  await expect(page.getByText(/No se pudo generar el informe|error/i)).toHaveCount(0);
});

test('pagar ahora (simulador): tarjeta que rechaza → KO con reintento', async ({ page }) => {
  await irAlSimuladorDeUnaReservaPendiente(page);

  await page.getByLabel('Número de tarjeta').fill(TARJETA_RECHAZA);
  await page.getByLabel('Caducidad (MM/AA)').fill('12/30');
  await page.getByLabel('CVC').fill('123');
  await page.getByRole('button', { name: /^Pagar$/ }).click();

  // DECLINED → pantalla KO con botón Reintentar (no se ha cobrado, sigue en el simulador).
  await expect(page.getByRole('button', { name: /Reintentar/i })).toBeVisible();
});
