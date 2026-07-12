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
  // todas las franjas son futuras y reservables.
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const yyyyMmDd = tomorrow.toISOString().slice(0, 10);
  await page.locator('#disp-fecha').fill(yyyyMmDd);

  // Esperar a que cargue la lista (o el estado vacío). El botón "Reservar HH:MM"
  // solo aparece en tramos creables.
  const reservarBtn = page.getByRole('button', { name: /^Reservar \d{2}:\d{2}$/ }).first();
  await expect(
    reservarBtn,
    'No hay ninguna franja reservable para hoy: revisa el seed de SystemConfig/disponibilidad en el stack de prueba.'
  ).toBeVisible();
  await reservarBtn.click();

  // 3) Confirmar la reserva
  await expect(page).toHaveURL(/\/reservas\/confirmar/);
  const confirmarBtn = page.getByRole('button', { name: /^Confirmar reserva$/ });
  await expect(confirmarBtn).toBeVisible();
  await confirmarBtn.click();

  // 4) Verificar la pantalla de éxito (reserva pendiente de confirmación por el club).
  //    Si el journey se rompe (p. ej. estilo #201), aquí aparecería el error genérico
  //    "No se pudo completar la reserva" y el test fallaría con traza.
  await expect(
    page.getByRole('heading', { name: /Reserva pendiente de confirmaci/i })
  ).toBeVisible();
  await expect(page.getByText(/No se pudo completar la reserva/i)).toHaveCount(0);
});
