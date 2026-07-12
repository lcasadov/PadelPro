import { test, expect } from '@playwright/test';
import { login } from './helpers';

// Camino "aún no vinculado" (change e2e-telegram, capability auth-otp-telegram).
//
// Journey por UI: iniciar la vinculación desde el perfil (la pantalla muestra el código
// /vincular <otpCode>) y pulsar "comprobar vinculación" SIN que ningún webhook haya
// vinculado la cuenta. El botón re-consulta GET /api/usuarios/me; como `telegramLinked`
// sigue false, la pantalla muestra el aviso "Aún no detectamos la vinculación".
//
// Re-ejecutable: si un run previo dejó la cuenta vinculada, se desvincula primero. No
// completa la vinculación, así que deja la cuenta como la encontró (no vinculada).
test('perfil: comprobar vinculación sin webhook muestra "aún no detectamos la vinculación"', async ({
  page,
}) => {
  await login(page);

  // Home → Mi perfil (avatar del header; aria-label exacto para no colisionar con la
  // tarjeta "Mi perfil · Datos y configuración").
  await page.getByRole('link', { name: 'Mi perfil', exact: true }).click();
  await expect(page).toHaveURL(/\/perfil/);

  // Garantizar estado de partida "no vinculado".
  const desvincular = page.getByRole('button', { name: /^Desvincular$/ });
  if ((await desvincular.count()) > 0) {
    await desvincular.click();
    await expect(page.getByRole('button', { name: /^Vincular$/ })).toBeVisible();
  }

  // Iniciar la vinculación → pantalla con el código generado por el backend.
  await page.getByRole('button', { name: /^Vincular$/ }).click();
  await expect(page).toHaveURL(/\/perfil\/telegram\/vincular/);
  await expect(
    page.getByRole('heading', { name: /Activa Telegram en tu cuenta/i })
  ).toBeVisible();
  await expect(page.getByText(/\/vincular/).first()).toBeVisible();

  // Comprobar SIN que el webhook haya vinculado → aviso de "aún no".
  await page.getByRole('button', { name: /comprobar vinculación/i }).click();
  await expect(page.getByText(/Aún no detectamos la vinculación/i)).toBeVisible();

  // No se muestra la vista de éxito (la cuenta sigue no vinculada).
  await expect(page.getByRole('heading', { name: /^Telegram vinculado$/ })).toHaveCount(0);
});
