import { test, expect } from '@playwright/test';

// Seguridad del webhook de Telegram (change e2e-telegram, capability auth-otp-telegram,
// RN-TEL-01). El endpoint POST /api/bot/telegram es `permitAll` (Telegram no envía JWT):
// su única protección es el header `X-Telegram-Bot-Api-Secret-Token`, validado en el
// servicio contra `system_config.telegram_webhook_secret`. Un header ausente o incorrecto
// debe rechazarse con 403 ANTES de parsear el update.
//
// No requiere login ni configurar nada: se ejercita puramente por API con el fixture
// `request` (respeta el baseURL del stack real). Es re-ejecutable y no deja estado.
test('webhook telegram: 403 sin header secret y con header incorrecto (RN-TEL-01)', async ({
  request,
}) => {
  // Update de Telegram bien formado; lo que se prueba es SOLO el gate del secret.
  const update = {
    update_id: 1,
    message: { message_id: 1, chat: { id: 987654321 }, text: 'hola' },
  };

  // Sin el header secret → 403 (reason=missing_header).
  const sinHeader = await request.post('/api/bot/telegram', { data: update });
  expect(sinHeader.status()).toBe(403);

  // Con el header pero valor incorrecto → 403 (reason=mismatch, comparación en tiempo
  // constante). Nunca vincula ni procesa el comando.
  const headerMalo = await request.post('/api/bot/telegram', {
    headers: { 'X-Telegram-Bot-Api-Secret-Token': 'valor-incorrecto' },
    data: update,
  });
  expect(headerMalo.status()).toBe(403);
});
