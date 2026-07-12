import { test, expect } from '@playwright/test';
import { login } from './helpers';

// Journey: listado de partidas abiertas (change e2e-journeys, capability
// partidas-unirse).
//
// Se verifica que la página carga y presenta un estado válido: o bien el listado
// de partidas con plazas libres, o el estado vacío neutro. NO se prueba la acción
// de "unirse": requeriría una partida creada por OTRO usuario (uno no puede unirse
// a su propia reserva) y el stack solo tiene sembrado el admin de bootstrap, así
// que no es posible montar ese escenario de forma determinista sin un segundo
// usuario. Esa cobertura queda fuera de este journey a propósito.
test('la página de partidas abiertas carga (listado o estado vacío)', async ({ page }) => {
  await login(page);

  // Home → Partidas abiertas (navegación cliente).
  await page.getByRole('link', { name: /Partidas abiertas/i }).click();
  await expect(page).toHaveURL(/\/reservas\/partidas/);

  await expect(page.getByRole('heading', { name: /Partidas abiertas/i })).toBeVisible();

  // Estado válido: listado (tarjetas con data-partida) o vacío neutro.
  const vacio = page.getByText(/No hay partidas abiertas para esta fecha/i);
  const tarjetas = page.locator('[data-partida]');
  await expect(vacio.or(tarjetas.first())).toBeVisible();
});
