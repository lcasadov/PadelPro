import { test, expect } from '@playwright/test';
import { login, mananaISO } from './helpers';

// Journey de bloqueo de franjas por el ADMIN (change bloqueos-pista-eventos):
// login admin → /admin/bloqueos → fecha mañana → bloquear una franja LIBRE con motivo
// → queda "Bloqueada" → como jugador, esa franja YA NO aparece en disponibilidad →
// desbloquear → reaparece. Determinista y autocontenido sobre el stack real.
//
// Se bloquea una hora fija tardía (20:00), libre en un día sin reservas, para no
// depender de qué franja sea "la primera libre". Tras filfar la fecha se espera a que
// la recarga de la rejilla (disponibilidad + bloqueos) termine antes de interactuar
// (la recarga limpia la selección; interactuar antes provocaría una carrera).

const HORA = '20:00';

async function irABloqueosYFijarFecha(page: import('@playwright/test').Page, fecha: string) {
  await page.getByRole('link', { name: /Bloquear franjas/i }).click();
  await expect(page).toHaveURL(/\/admin\/bloqueos/);
  // Fijar la fecha y esperar a que la recarga (GET /admin/bloqueos?fecha=) termine.
  await Promise.all([
    page.waitForResponse(
      (r) =>
        r.url().includes('/api/admin/bloqueos') &&
        r.url().includes(fecha) &&
        r.request().method() === 'GET'
    ),
    page.locator('input[type="date"]').fill(fecha),
  ]);
}

test('bloqueos: el admin bloquea una franja y desaparece de la disponibilidad', async ({ page }) => {
  await login(page);
  const fecha = mananaISO();

  await irABloqueosYFijarFecha(page, fecha);

  // Bloquear la franja fija (libre en un día sin reservas) con un motivo.
  await page.getByRole('checkbox', { name: `Bloquear ${HORA}` }).check();
  await page.getByLabel(/Motivo/i).fill('Torneo E2E');
  await page.getByRole('button', { name: /^Bloquear \d+ franjas?$/ }).click();

  // La recarga posterior al bloqueo deja la franja como "Bloqueada" (resultado real;
  // no se comprueba el toast porque comparte role=status con el spinner de carga).
  const slotBloqueado = page.locator('li', { hasText: HORA });
  await expect(slotBloqueado.getByText(/Bloqueada/i)).toBeVisible();

  // Como jugador: la disponibilidad de mañana ya NO ofrece esa franja.
  await page.getByRole('link', { name: /Volver al inicio/i }).click();
  await expect(page).toHaveURL(/\/home/);
  await page.getByRole('button', { name: /reservar pista/i }).click();
  await expect(page).toHaveURL(/\/reservas\/disponibilidad/);
  await page.locator('#disp-fecha').fill(fecha);
  await expect(page.getByRole('button', { name: `Reservar ${HORA}` })).toHaveCount(0);

  // Desbloquear: volver a /admin/bloqueos y liberar la franja → reaparece como LIBRE.
  await page.getByRole('button', { name: /^Inicio$/ }).click();
  await expect(page).toHaveURL(/\/home/);
  await irABloqueosYFijarFecha(page, fecha);
  const slot = page.locator('li', { hasText: HORA });
  await slot.getByRole('button', { name: /Desbloquear/i }).click();
  await expect(page.getByRole('checkbox', { name: `Bloquear ${HORA}` })).toBeVisible();
});
