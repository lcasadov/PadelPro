import { test, expect, type APIRequestContext } from '@playwright/test';
import { login, EMAIL, PASSWORD } from './helpers';

// Round-trip completo de vinculación de Telegram (change e2e-telegram, capability
// auth-otp-telegram). El TEST HACE DE BOT: como no hay bot real, simula el mensaje
// entrante contra el webhook POST /api/bot/telegram con el secret configurado y el
// comando `/vincular <otpCode>`, completando la vinculación end-to-end.
//
// Flujo:
//   1. (setup API) login admin → fijar `telegram_webhook_secret` vía la API admin de
//      configuración (PATCH /api/admin/sistema/config), preservando el resto de campos.
//   2. (UI) login → perfil → asegurar "no vinculado" → "Vincular": la pantalla genera el
//      OTP; el test LEE el `otpCode` interceptando la respuesta del PATCH /api/usuarios/me
//      {LINK} (nunca hardcodeado).
//   3. (API, actuando como bot) POST /api/bot/telegram con el secret y el update de
//      Telegram { message.chat.id, message.text:'/vincular <otpCode>' }.
//   4. (UI) "comprobar vinculación" → la pantalla muestra la vista de éxito
//      "Telegram vinculado"; se corrobora vía GET /api/usuarios/me → telegramLinked:true.
//   5. (cleanup) DESVINCULAR (PATCH /api/usuarios/me {UNLINK}) → la cuenta queda como
//      estaba (no vinculada). Re-ejecutable sobre la misma BD.
//
// Nota: el `telegram_webhook_secret` queda configurado tras el test. Es inocuo (no hay
// forma de "desconfigurarlo" por API — enviar blank se ignora) y no afecta a los demás
// specs, que verifican el 403 (header ausente/incorrecto) o el camino "no vinculado".

const WEBHOOK_SECRET = 'e2e-telegram-roundtrip-secret';
// chat_id de test estable. El backend rechaza vincular un chat ya usado por OTRA cuenta;
// aquí siempre es la misma (admin), y se desvincula al final, así que es seguro reusarlo.
const TEST_CHAT_ID = 987654321;

/**
 * Login admin por API devolviendo el bearer token. Resiliente al rate-limit de
 * /api/auth/login (5/min/IP): ante 429 espera el refill y reintenta, igual que el
 * `login()` por UI de helpers.ts.
 */
async function apiLogin(request: APIRequestContext): Promise<string> {
  for (let intento = 0; intento < 5; intento++) {
    const resp = await request.post('/api/auth/login', {
      data: { email: EMAIL, password: PASSWORD },
    });
    if (resp.status() !== 429) {
      expect(resp.ok(), 'login admin por API debe responder 2xx').toBeTruthy();
      const body = (await resp.json()) as { access_token: string };
      return body.access_token;
    }
    const retryAfter = Number(resp.headers()['retry-after']);
    const waitSecs = Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : 12;
    await new Promise((r) => setTimeout(r, Math.min(waitSecs, 15) * 1000 + 500));
  }
  throw new Error('Login admin por API bloqueado por rate-limit (429) tras varios reintentos.');
}

test('perfil: round-trip de vinculación de Telegram (el test hace de bot vía webhook)', async ({
  page,
}) => {
  const request = page.request;

  // ── 1. Configurar el webhook secret (setup por API admin) ───────────────────────
  const token = await apiLogin(request);

  // Preservar el resto de la config: se re-envían los campos actuales + el secret. GET
  // enmascara los secretos como flags, pero con paymentGateway != REDSYS no hacen falta.
  const cfgResp = await request.get('/api/admin/sistema/config', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(cfgResp.ok(), 'GET config admin debe responder 2xx').toBeTruthy();
  const cfg = (await cfgResp.json()) as {
    clubName: string;
    clubDescription: string;
    pistaState: string;
    paymentGateway: string;
    maxParticipantsPerPista: number;
    pricePerHour: number;
    cancellationDeadlineHours: number;
  };

  const patchCfg = await request.patch('/api/admin/sistema/config', {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: {
      clubName: cfg.clubName,
      clubDescription: cfg.clubDescription,
      pistaState: cfg.pistaState,
      paymentGateway: cfg.paymentGateway,
      maxParticipantsPerPista: cfg.maxParticipantsPerPista,
      pricePerHour: cfg.pricePerHour,
      cancellationDeadlineHours: cfg.cancellationDeadlineHours,
      telegramWebhookSecret: WEBHOOK_SECRET,
    },
  });
  expect(patchCfg.ok(), 'PATCH config con el webhook secret debe responder 2xx').toBeTruthy();

  // ── 2. Iniciar la vinculación por UI y leer el otpCode dinámicamente ─────────────
  await login(page);
  await page.getByRole('link', { name: 'Mi perfil', exact: true }).click();
  await expect(page).toHaveURL(/\/perfil/);

  // Asegurar estado de partida "no vinculado".
  const desvincular = page.getByRole('button', { name: /^Desvincular$/ });
  if ((await desvincular.count()) > 0) {
    await desvincular.click();
    await expect(page.getByRole('button', { name: /^Vincular$/ })).toBeVisible();
  }

  // "Vincular" navega a la pantalla que en su useEffect llama a PATCH /api/usuarios/me
  // {telegramAction:'LINK'}; interceptamos esa respuesta para leer el otpCode real.
  const [linkResp] = await Promise.all([
    page.waitForResponse(
      (r) =>
        r.url().includes('/api/usuarios/me') &&
        r.request().method() === 'PATCH' &&
        (r.request().postData() ?? '').includes('LINK')
    ),
    page.getByRole('button', { name: /^Vincular$/ }).click(),
  ]);
  const { otpCode } = (await linkResp.json()) as { otpCode?: string };
  expect(otpCode, 'el backend debe devolver el otpCode al iniciar la vinculación').toMatch(
    /^\d{6}$/
  );

  await expect(page).toHaveURL(/\/perfil\/telegram\/vincular/);
  await expect(page.getByText(/\/vincular/).first()).toBeVisible();

  // ── 3. Actuar como el bot: POST al webhook con el secret y el comando de vinculación
  const webhookResp = await request.post('/api/bot/telegram', {
    headers: { 'X-Telegram-Bot-Api-Secret-Token': WEBHOOK_SECRET },
    data: {
      update_id: Date.now(),
      message: {
        message_id: 1,
        chat: { id: TEST_CHAT_ID, type: 'private' },
        text: `/vincular ${otpCode}`,
      },
    },
  });
  expect(webhookResp.status(), 'el webhook con secret válido debe responder 200').toBe(200);

  // ── 4. Comprobar en la UI → vista de éxito; corroborar por API ───────────────────
  await page.getByRole('button', { name: /comprobar vinculación/i }).click();
  await expect(page.getByRole('heading', { name: /^Telegram vinculado$/ })).toBeVisible();

  const meResp = await request.get('/api/usuarios/me', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const me = (await meResp.json()) as { telegramLinked: boolean };
  expect(me.telegramLinked, 'GET /me debe reflejar telegramLinked:true').toBe(true);

  // ── 5. Desvincular (cleanup) → la cuenta vuelve a "no vinculada" ─────────────────
  const unlink = await request.patch('/api/usuarios/me', {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { telegramAction: 'UNLINK' },
  });
  expect(unlink.ok(), 'UNLINK debe responder 2xx').toBeTruthy();

  const meAfter = await request.get('/api/usuarios/me', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const meAfterBody = (await meAfter.json()) as { telegramLinked: boolean };
  expect(meAfterBody.telegramLinked, 'tras UNLINK telegramLinked debe ser false').toBe(false);
});
