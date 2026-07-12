// Helpers compartidos de los journeys E2E (change e2e-journeys).
//
// Corren en un navegador REAL contra el stack REAL (frontend build + backend +
// postgres), sin MSW ni stubs. Reutilizan el patrón del smoke crear-reserva:
//   - login por UI (el access token vive SOLO en memoria — RN-AUTH-09 — así que
//     NUNCA se usa page.goto a rutas privadas: una recarga cerraría sesión),
//   - reservas para MAÑANA (el backend rechaza el pasado),
//   - bucle de reintento sobre 409 ("La franja se acaba de ocupar") para ser
//     re-ejecutable sobre una BD sucia (local, sin resetear).
//
// Este fichero NO es un *.spec.ts, así que Playwright no lo ejecuta como test.
import { expect, type Page } from '@playwright/test';

// Credenciales del ADMIN de bootstrap (mismos defaults que el smoke).
export const EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'e2e-admin@padelpro.local';
export const PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'E2ePassw0rd!secure';

/** Fecha de MAÑANA en formato YYYY-MM-DD (todas las franjas son futuras). */
export function mananaISO(): string {
  const t = new Date();
  t.setDate(t.getDate() + 1);
  return t.toISOString().slice(0, 10);
}

/**
 * Inicia sesión por la UI y espera la redirección a /home (o a cambio de
 * contraseña forzado). No usa page.goto salvo para la ruta pública /login.
 *
 * Resiliencia a rate-limit (RN-AUTH-09): el backend limita POST /api/auth/login a
 * 5 req/min/IP (RateLimitFilter, Bucket4j, refill continuo ~1 token/12s). Al correr
 * TODA la suite, la 6ª+ sesión en la misma ventana recibe 429 ("Demasiados
 * intentos"). Para que cada spec sea re-ejecutable y la suite completa pase en
 * verde sin depender del orden, ante un 429 esperamos el refill (Retry-After) y
 * reintentamos. Ejecutado en aislamiento (un solo login) nunca llega a esperar.
 */
export async function login(
  page: Page,
  email: string = EMAIL,
  password: string = PASSWORD
): Promise<void> {
  await page.goto('/login');

  const MAX_INTENTOS = 5;
  for (let intento = 0; intento < MAX_INTENTOS; intento++) {
    await page.locator('#login-email').fill(email);
    await page.locator('#login-password').fill(password);

    const [resp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST'
      ),
      page.getByRole('button', { name: /entrar/i }).click(),
    ]);

    if (resp.status() !== 429) {
      await expect(page).toHaveURL(/\/(home|cambiar-password)/);
      return;
    }

    // 429: esperar el refill de un token (Retry-After en segundos) y reintentar.
    const retryAfter = Number(resp.headers()['retry-after']);
    const waitSecs = Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : 12;
    await page.waitForTimeout(Math.min(waitSecs, 15) * 1000 + 500);
  }

  throw new Error(
    'Login bloqueado por rate-limit (429) tras varios reintentos. ¿Suite lanzada muchas veces en <1 min?'
  );
}

/**
 * Crea una reserva para MAÑANA navegando por la UI desde Home y devuelve el id de
 * la reserva creada (leído de la respuesta del POST /api/reservas). Tolera 409
 * probando franjas sucesivas, de modo que es re-ejecutable sobre una BD reutilizada.
 * Al volver deja la pantalla en la confirmación de éxito (botón "Ver mis reservas").
 */
export async function crearReservaDesdeHome(page: Page): Promise<string> {
  await page.getByRole('button', { name: /reservar pista/i }).click();
  await expect(page).toHaveURL(/\/reservas\/disponibilidad/);

  const fecha = mananaISO();
  const MAX_INTENTOS = 15; // ~slots de un día

  for (let i = 0; i < MAX_INTENTOS; i++) {
    // (Re)fijar la fecha a mañana: volver de un conflicto reinicia el selector a hoy.
    await page.locator('#disp-fecha').fill(fecha);

    const botones = page.getByRole('button', { name: /^Reservar \d{2}:\d{2}$/ });
    await expect(
      botones.first(),
      'No hay franjas reservables para mañana (¿día lleno? resetea la BD: docker compose down -v).'
    ).toBeVisible();

    if (i >= (await botones.count())) break; // no quedan franjas por probar
    await botones.nth(i).click();

    await expect(page).toHaveURL(/\/reservas\/confirmar/);

    const [resp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/reservas') && r.request().method() === 'POST'
      ),
      page.getByRole('button', { name: /^Confirmar reserva$/ }).click(),
    ]);

    // Resultado: éxito o conflicto. Si el journey se rompe aparecería el error
    // genérico y ninguno de los dos → timeout claro.
    const exito = page.getByRole('heading', { name: /Reserva pendiente de confirmaci/i });
    const conflicto = page.getByRole('heading', { name: /La franja se acaba de ocupar/i });
    await expect(exito.or(conflicto)).toBeVisible();
    await expect(page.getByText(/No se pudo completar la reserva/i)).toHaveCount(0);

    if (resp.ok()) {
      const body = (await resp.json()) as { id: string };
      return body.id;
    }

    // Franja ocupada (409): volver a disponibilidad y probar la siguiente.
    await page.getByRole('button', { name: /Volver a buscar disponibilidad/i }).click();
    await expect(page).toHaveURL(/\/reservas\/disponibilidad/);
  }

  throw new Error(
    'No se pudo crear la reserva tras probar varias franjas (¿día lleno? resetea la BD: docker compose down -v).'
  );
}
