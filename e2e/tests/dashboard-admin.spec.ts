import { test, expect } from '@playwright/test';
import { login } from './helpers';

// Journey COMPLETO: dashboard del club — ADMIN ve métricas reales y exporta CSV
// (change e2e-journeys, capability administracion-club).
//
// Este spec cazó un bug real de #217: los endpoints GET /api/admin/dashboard/**
// devolvían 500 (enmascarado como 401 por el error-dispatch) porque las queries
// JPQL casteaban a un tipo enum de Postgres inexistente. Se corrigió (parámetro
// vinculado en vez de literal de enum) + IT de regresión. Ahora el journey es
// completo: las métricas cargan y el export CSV descarga.
test('admin: dashboard del club carga métricas y exporta CSV', async ({ page }) => {
  await login(page);

  // Entrada solo-ADMIN en Home → navegación cliente (no page.goto). Valida RBAC UI + AdminRoute.
  await page.getByRole('link', { name: /Dashboard del club/i }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/);

  // Estructura de la pantalla.
  await expect(page.getByRole('heading', { name: /Dashboard del club/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: /^Ocupación$/ })).toBeVisible();
  await expect(page.getByRole('heading', { name: /^Ingresos$/ })).toBeVisible();

  // Métricas CARGADAS (no el estado de error ni el spinner): el importe de ingresos
  // se formatea en euros (€) y la ocupación en porcentaje (%). Si el backend fallara
  // (el bug de #217) se mostraría la alerta de error y placeholders "—".
  await expect(page.getByText(/No se pudieron cargar las métricas/i)).toHaveCount(0);
  await expect(page.getByText(/€/).first()).toBeVisible();
  await expect(page.getByText(/%/).first()).toBeVisible();

  // Export CSV: la descarga se dispara (prueba que GET /exportar responde 200 CSV
  // como attachment). waitForEvent('download') solo resuelve si hay descarga real.
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: /Exportar CSV/i }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/informe-uso.*\.csv/i);
  await expect(page.getByText(/No se pudo generar el informe CSV/i)).toHaveCount(0);
});
