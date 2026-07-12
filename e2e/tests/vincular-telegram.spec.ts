import { test, expect } from '@playwright/test';
import { login } from './helpers';

// Journey: iniciar la vinculación de Telegram desde el perfil (change e2e-journeys,
// capability auth-otp-telegram).
//
// Sin token de bot configurado el envío por Telegram queda SKIPPED, pero el PATCH
// /api/usuarios/me {telegramAction:'LINK'} sigue devolviendo el otpCode en la
// respuesta, así que la pantalla de vinculación puede mostrar el comando
// "/vincular <código>". No se completa la vinculación real (haría falta el webhook
// del bot): solo se verifica que la pantalla del paso aparece.
test('perfil: iniciar vinculación de Telegram muestra el paso con el código', async ({
  page,
}) => {
  await login(page);

  // Home → Mi perfil (avatar del header; aria-label exacto "Mi perfil" para no
  // colisionar con la tarjeta "Mi perfil · Datos y configuración").
  await page.getByRole('link', { name: 'Mi perfil', exact: true }).click();
  await expect(page).toHaveURL(/\/perfil/);

  // Re-ejecutable: si un run previo dejó Telegram vinculado, desvincular primero
  // para que aparezca el botón "Vincular". (En la práctica la vinculación nunca se
  // completa sin webhook, así que normalmente ya está en "Vincular".)
  const desvincular = page.getByRole('button', { name: /^Desvincular$/ });
  if ((await desvincular.count()) > 0) {
    await desvincular.click();
    await expect(page.getByRole('button', { name: /^Vincular$/ })).toBeVisible();
  }

  // Iniciar la vinculación.
  await page.getByRole('button', { name: /^Vincular$/ }).click();
  await expect(page).toHaveURL(/\/perfil\/telegram\/vincular/);

  // Pantalla de vinculación: hero + comando "/vincular <código>" generado por el
  // backend (el OTP viene en la respuesta del PATCH aunque no haya bot).
  await expect(
    page.getByRole('heading', { name: /Activa Telegram en tu cuenta/i })
  ).toBeVisible();
  await expect(page.getByText(/\/vincular/).first()).toBeVisible();
  // El paso 3 ofrece comprobar la vinculación (prueba que el código no ha caducado).
  await expect(
    page.getByRole('button', { name: /comprobar vinculación/i })
  ).toBeVisible();
});
