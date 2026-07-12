import { test, expect } from '@playwright/test';
import { EMAIL } from './helpers';

// Journey: login con contraseña incorrecta (change e2e-journeys).
//
// El backend responde 401 y el frontend muestra un mensaje genérico de
// "Credenciales inválidas" (anti-enumeración: no revela si el email existe —
// RN-AUTH). No debe navegar a /home: la sesión no se abre.
test('login con password incorrecta muestra "Credenciales inválidas" y no entra', async ({
  page,
}) => {
  await page.goto('/login');

  // Email válido (el del admin de bootstrap) pero contraseña incorrecta: fuerza el
  // 401 real del backend, no un 403 de cuenta inactiva ni un 400 de validación.
  await page.locator('#login-email').fill(EMAIL);
  await page.locator('#login-password').fill('password-incorrecta-000');
  await page.getByRole('button', { name: /entrar/i }).click();

  // Mensaje genérico de credenciales (role="alert").
  await expect(page.getByRole('alert')).toHaveText(/Credenciales inválidas/i);

  // No se abre sesión: seguimos en /login (nunca /home).
  await expect(page).toHaveURL(/\/login/);
});
