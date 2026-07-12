import { test, expect } from '@playwright/test';

// Smoke E2E del journey crítico: iniciar sesión -> crear reserva (change e2e-smoke-tests, #205).
//
// Corre en un navegador REAL contra el stack REAL (frontend build + backend + postgres),
// sin MSW ni stubs. Es la red de seguridad que faltaba: los tests de componente con mocks
// pasaban en verde mientras el journey real fallaba (bug #201).
//
// Credenciales: el backend arranca con ADMIN_EMAIL/ADMIN_PASSWORD (bootstrap de un ADMIN
// ACTIVE). /api/reservas solo exige autenticación, así que el ADMIN puede reservar.
const EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'e2e-admin@padelpro.local';
const PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'E2ePassw0rd!secure';

test('login y crear reserva llega a la confirmación', async ({ page }) => {
  // 1) Login
  await page.goto('/login');
  await page.locator('#login-email').fill(EMAIL);
  await page.locator('#login-password').fill(PASSWORD);
  await page.getByRole('button', { name: /entrar/i }).click();

  // Tras autenticarse, la app redirige a /home (o a cambio de contraseña forzado).
  await expect(page).toHaveURL(/\/(home|cambiar-password)/);

  // 2) Navegar a disponibilidad DESDE LA UI (routing cliente). Importante: NO usar
  //    page.goto — una recarga completa borra el access token (vive solo en memoria,
  //    RN-AUTH-09) y la app redirigiría a /login.
  await page.getByRole('button', { name: /reservar pista/i }).click();
  await expect(page).toHaveURL(/\/reservas\/disponibilidad/);

  // Elegir MAÑANA: el backend rechaza reservas en el pasado ("la fecha y hora deben
  // estar en el futuro"), así que las franjas de hoy ya vencidas fallarían. Con mañana,
  // todas las franjas son futuras.
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const fecha = tomorrow.toISOString().slice(0, 10);

  // 3) Reservar la primera franja LIBRE, tolerando 409.
  //    El test crea una reserva REAL, así que en una BD reutilizada (local, sin resetear)
  //    las primeras franjas pueden estar ya ocupadas y el backend responde 409
  //    ("La franja se acaba de ocupar", el gist anti-solape es la autoridad aunque la
  //    disponibilidad marque la franja como creable). Se prueban slots sucesivos hasta
  //    que uno confirme. En CI la BD es fresca y confirma al primer intento.
  const MAX_INTENTOS = 15; // ~slots de un día
  let reservado = false;

  for (let i = 0; i < MAX_INTENTOS && !reservado; i++) {
    // (Re)fijar la fecha a mañana: volver de un conflicto reinicia el selector a hoy.
    await page.locator('#disp-fecha').fill(fecha);

    const botones = page.getByRole('button', { name: /^Reservar \d{2}:\d{2}$/ });
    await expect(
      botones.first(),
      'No hay franjas reservables para mañana (¿día lleno? resetea la BD: docker compose down -v).'
    ).toBeVisible();

    if (i >= (await botones.count())) break; // no quedan franjas por probar
    await botones.nth(i).click();

    // Confirmar
    await expect(page).toHaveURL(/\/reservas\/confirmar/);
    await page.getByRole('button', { name: /^Confirmar reserva$/ }).click();

    // Resultado: éxito o conflicto (franja ya ocupada). Si el journey se rompe
    // (estilo #201) aparecería el error genérico y ninguno de los dos → timeout claro.
    const exito = page.getByRole('heading', { name: /Reserva pendiente de confirmaci/i });
    const conflicto = page.getByRole('heading', { name: /La franja se acaba de ocupar/i });
    await expect(exito.or(conflicto)).toBeVisible();
    await expect(page.getByText(/No se pudo completar la reserva/i)).toHaveCount(0);

    if (await exito.isVisible()) {
      reservado = true;
    } else {
      // Franja ocupada: volver a disponibilidad y probar la siguiente.
      await page.getByRole('button', { name: /Volver a buscar disponibilidad/i }).click();
      await expect(page).toHaveURL(/\/reservas\/disponibilidad/);
    }
  }

  expect(
    reservado,
    'No se pudo crear la reserva tras probar varias franjas (¿día lleno? resetea la BD: docker compose down -v).'
  ).toBe(true);
});
