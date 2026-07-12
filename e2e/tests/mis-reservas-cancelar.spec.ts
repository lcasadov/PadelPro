import { test, expect } from '@playwright/test';
import { login, crearReservaDesdeHome } from './helpers';

// Journey: crear una reserva y luego cancelarla desde "Mis reservas"
// (change e2e-journeys).
//
// Autocontenido y re-ejecutable: crea su propia reserva (patrón de reintento del
// helper) y la localiza en la lista por su id, así no depende de datos previos ni
// interfiere con otras reservas de una BD sucia.
test('crear una reserva y cancelarla desde Mis reservas', async ({ page }) => {
  await login(page);

  // 1) Crear la reserva (deja la pantalla en la confirmación de éxito).
  const reservaId = await crearReservaDesdeHome(page);

  // 2) Ir a "Mis reservas" por la UI (botón de la pantalla de éxito).
  await page.getByRole('button', { name: /Ver mis reservas/i }).click();
  await expect(page).toHaveURL(/\/reservas\/mias/);

  // 3) Localizar la tarjeta de LA reserva creada (su id se muestra en la tarjeta).
  const card = page.locator('li', { hasText: reservaId });
  await expect(card).toBeVisible();
  // Recién creada → estado "Pendiente de confirmación" y acción de cancelar.
  await expect(card.getByText(/Pendiente de confirmación/i)).toBeVisible();

  // 4) Cancelar (flujo de confirmación inline: "Cancelar" → "Confirmar cancelación").
  await card.getByRole('button', { name: /^Cancelar$/ }).click();
  await card.getByRole('button', { name: /Confirmar cancelación/i }).click();

  // 5) Tras cancelar, la lista se recarga: la reserva pasa a "Cancelada" y ya no
  //    ofrece la acción de cancelar.
  const cardTrasCancelar = page.locator('li', { hasText: reservaId });
  await expect(cardTrasCancelar.getByText(/Cancelada/i)).toBeVisible();
  await expect(cardTrasCancelar.getByRole('button', { name: /^Cancelar$/ })).toHaveCount(0);
});
